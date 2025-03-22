package com.jumps.videoinspector;

import android.content.Context;
import android.content.pm.ActivityInfo;
import android.graphics.SurfaceTexture;
import android.hardware.usb.UsbDevice;
import android.opengl.GLSurfaceView;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.view.Surface;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import com.serenegiant.common.BaseActivity;
import com.serenegiant.usb.CameraDialog;
import com.serenegiant.usb.IButtonCallback;
import com.serenegiant.usb.IInspectionFrameCallback;
import com.serenegiant.usb.IStatusCallback;
import com.serenegiant.usb.USBMonitor;
import com.serenegiant.usb.USBMonitor.OnDeviceConnectListener;
import com.serenegiant.usb.USBMonitor.UsbControlBlock;
import com.serenegiant.usb.UVCCamera2;
import com.serenegiant.widget.SimpleUVCCameraTextureView;
import com.serenegiant.widget.YUYVRenderer;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;

public final class MainActivity extends BaseActivity implements CameraDialog.CameraDialogParent {

	private static final String TAG = "MainActivity";
	private final Object mSync = new Object();
    // for accessing USB and USB camera
    private USBMonitor mUSBMonitor;
	private UVCCamera2 mUVCCamera;
	private SimpleUVCCameraTextureView mUVCCameraView;
	// for open&start / stop&close camera preview
	private ImageButton mCameraButton;
	private Surface mPreviewSurface;

	private Button mInspectionButton;
	private Button mPrevFrameButton;
	private Button mNextFrameButton;

	private Button mRotateClockwiseButton;
	private Button mRotateCounterClockwiseButton;

	private Button mFlipHorzButton;
	private Button mFlipVertButton;

	private GLSurfaceView mInspectionView;
	private YUYVRenderer mYUYVRenderer;
	private int mCurInspectionFrameIndex = 0;
	private int mTotalInspectionFramesCount = 0;
	private TextView mInspectionFramesText;

	@Override
	protected void onCreate(final Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		// setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
		requestWindowFeature(Window.FEATURE_NO_TITLE);
		getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
				WindowManager.LayoutParams.FLAG_FULLSCREEN);

		setContentView(R.layout.activity_main);
		mCameraButton = (ImageButton)findViewById(R.id.camera_button);
		mCameraButton.setOnClickListener(mOnClickListener);

		mUVCCameraView = (SimpleUVCCameraTextureView)findViewById(R.id.camera_preview);
		mUVCCameraView.setAspectRatio(UVCCamera2.DEFAULT_PREVIEW_WIDTH / (float)UVCCamera2.DEFAULT_PREVIEW_HEIGHT);
		// mUVCCameraView.setAspectRatio(1280 / 720.0f);

		mUSBMonitor = new USBMonitor(this, mOnDeviceConnectListener);

		mInspectionButton = (Button) findViewById(R.id.record_button);
		mInspectionButton.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				synchronized (mSync) {
					if (mUVCCamera != null) {
						mUVCCamera.startInspection();
					}
				}
			}
		});

		mInspectionFramesText = (TextView) findViewById(R.id.inspect_frame_num);

		mPrevFrameButton = (Button) findViewById(R.id.backward_button);
		mPrevFrameButton.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				synchronized (mSync) {
					if (mUVCCamera != null) {
						if (mCurInspectionFrameIndex > 0) {
							mUVCCamera.getInspectionFrameAt(mCurInspectionFrameIndex-1);
						}
					}
				}
			}
		});

		mNextFrameButton = (Button) findViewById(R.id.forward_button);
		mNextFrameButton.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				synchronized (mSync) {
					if (mUVCCamera != null) {
						if (mCurInspectionFrameIndex < mTotalInspectionFramesCount) {
							mUVCCamera.getInspectionFrameAt(mCurInspectionFrameIndex+1);
						}
					}
				}
			}
		});

		mRotateClockwiseButton = (Button) findViewById(R.id.rotate_clockwise_button);
		mRotateClockwiseButton.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				if (mTotalInspectionFramesCount > 0) {
					YUYVRenderer.Rotation rotation = mYUYVRenderer.getRotation();
					if (rotation == YUYVRenderer.Rotation.Rotate_0)
						rotation = YUYVRenderer.Rotation.Rotate_270;
					else if (rotation == YUYVRenderer.Rotation.Rotate_270)
						rotation = YUYVRenderer.Rotation.Rotate_180;
					else if (rotation == YUYVRenderer.Rotation.Rotate_180)
						rotation = YUYVRenderer.Rotation.Rotate_90;
					else if (rotation == YUYVRenderer.Rotation.Rotate_90)
						rotation = YUYVRenderer.Rotation.Rotate_0;
					mYUYVRenderer.setRotation(rotation);
				}
			}
		});

		mRotateCounterClockwiseButton = (Button) findViewById(R.id.rotate_counterclockwise_button);
		mRotateCounterClockwiseButton.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				if (mTotalInspectionFramesCount > 0) {
					YUYVRenderer.Rotation rotation = mYUYVRenderer.getRotation();
					if (rotation == YUYVRenderer.Rotation.Rotate_0)
						rotation = YUYVRenderer.Rotation.Rotate_90;
					else if (rotation == YUYVRenderer.Rotation.Rotate_90)
						rotation = YUYVRenderer.Rotation.Rotate_180;
					else if (rotation == YUYVRenderer.Rotation.Rotate_180)
						rotation = YUYVRenderer.Rotation.Rotate_270;
					else if (rotation == YUYVRenderer.Rotation.Rotate_270)
						rotation = YUYVRenderer.Rotation.Rotate_0;
					mYUYVRenderer.setRotation(rotation);
				}
			}
		});

		// Flip horizontally button
		mFlipHorzButton = (Button) findViewById(R.id.flip_horz_button);
		mFlipHorzButton.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				if (mTotalInspectionFramesCount > 0) {
					boolean flipHorz = mYUYVRenderer.getFlipHorizontal();
					mYUYVRenderer.setFlipHorizontal(!flipHorz);
				}
			}
		});

		// Flip horizontally button
		mFlipVertButton = (Button) findViewById(R.id.flip_vert_button);
		mFlipVertButton.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				if (mTotalInspectionFramesCount > 0) {
					boolean flipVert = mYUYVRenderer.getFlipVertical();
					mYUYVRenderer.setFlipVertical(!flipVert);
				}
			}
		});

		mInspectionView = (GLSurfaceView) findViewById(R.id.inspection_view);
		mInspectionView.setEGLContextClientVersion(3);

		mYUYVRenderer = new YUYVRenderer(UVCCamera2.DEFAULT_PREVIEW_WIDTH, UVCCamera2.DEFAULT_PREVIEW_HEIGHT);
		mInspectionView.setRenderer(mYUYVRenderer);
	}

	@Override
	protected void onStart() {
		super.onStart();
		mUSBMonitor.register();
		synchronized (mSync) {
			if (mUVCCamera != null) {
				mUVCCamera.startPreview();
			}
		}
	}

	@Override
	protected void onStop() {
		synchronized (mSync) {
			if (mUVCCamera != null) {
				mUVCCamera.stopPreview();
			}
			if (mUSBMonitor != null) {
				mUSBMonitor.unregister();
			}
		}
		super.onStop();
	}

	@Override
	protected void onResume() {
		/**
		 * 设置为横屏
		 */
		if(getRequestedOrientation() != ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE) {
			setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
		}
		super.onResume();
		mInspectionView.onResume();
	}

	@Override
	protected void onPause() {
		super.onPause();
		mInspectionView.onPause();
	}

	@Override
	protected void onDestroy() {
		synchronized (mSync) {
			releaseCamera();
			if (mToast != null) {
				mToast.cancel();
				mToast = null;
			}
			if (mUSBMonitor != null) {
				mUSBMonitor.destroy();
				mUSBMonitor = null;
			}
		}
		mUVCCameraView = null;
		mCameraButton = null;
		super.onDestroy();
	}

	public void updateYUYVData(byte[] data) {
		if (mYUYVRenderer != null) {
			mYUYVRenderer.updateYUYVData(data);
		}
	}

	private final OnClickListener mOnClickListener = new OnClickListener() {
		@Override
		public void onClick(final View view) {
			synchronized (mSync) {
				if (mUVCCamera == null) {
					CameraDialog.showDialog(MainActivity.this);
				} else {
					releaseCamera();
				}
			}
		}
	};

	private Toast mToast;

	private final OnDeviceConnectListener mOnDeviceConnectListener = new OnDeviceConnectListener() {
		@Override
		public void onAttach(final UsbDevice device) {
			Toast.makeText(MainActivity.this, "USB_DEVICE_ATTACHED", Toast.LENGTH_SHORT).show();
		}

		@Override
		public void onConnect(final UsbDevice device, final UsbControlBlock ctrlBlock, final boolean createNew) {
			releaseCamera();
			queueEvent(new Runnable() {
				@Override
				public void run() {
					final UVCCamera2 camera = new UVCCamera2();
					camera.open(ctrlBlock);
					Log.i(TAG, "supportedSize:" + camera.getSupportedSize());
					camera.setStatusCallback(new IStatusCallback() {
						@Override
						public void onStatus(final int statusClass, final int event, final int selector,
											 final int statusAttribute, final ByteBuffer data) {
							runOnUiThread(new Runnable() {
								@Override
								public void run() {
									final Toast toast = Toast.makeText(MainActivity.this, "onStatus(statusClass=" + statusClass
											+ "; " +
											"event=" + event + "; " +
											"selector=" + selector + "; " +
											"statusAttribute=" + statusAttribute + "; " +
											"data=...)", Toast.LENGTH_SHORT);
									synchronized (mSync) {
										if (mToast != null) {
											mToast.cancel();
										}
										toast.show();
										mToast = toast;
									}
								}
							});
						}
					});
					camera.setButtonCallback(new IButtonCallback() {
						@Override
						public void onButton(final int button, final int state) {
							runOnUiThread(new Runnable() {
								@Override
								public void run() {
									final Toast toast = Toast.makeText(MainActivity.this, "onButton(button=" + button + "; " +
											"state=" + state + ")", Toast.LENGTH_SHORT);
									synchronized (mSync) {
										if (mToast != null) {
											mToast.cancel();
										}
										mToast = toast;
										toast.show();
									}
								}
							});
						}
					});
//					camera.setPreviewTexture(camera.getSurfaceTexture());
					if (mPreviewSurface != null) {
						mPreviewSurface.release();
						mPreviewSurface = null;
					}
					try {
						// camera.setPreviewSize(UVCCamera2.DEFAULT_PREVIEW_WIDTH, UVCCamera2.DEFAULT_PREVIEW_HEIGHT, UVCCamera2.FRAME_FORMAT_MJPEG);
						camera.setPreviewSize(UVCCamera2.DEFAULT_PREVIEW_WIDTH, UVCCamera2.DEFAULT_PREVIEW_HEIGHT,
								1, 121, UVCCamera2.FRAME_FORMAT_MJPEG, UVCCamera2.DEFAULT_BANDWIDTH);
					} catch (final IllegalArgumentException e) {
						// fallback to YUV mode
						try {
							camera.setPreviewSize(UVCCamera2.DEFAULT_PREVIEW_WIDTH, UVCCamera2.DEFAULT_PREVIEW_HEIGHT, UVCCamera2.DEFAULT_PREVIEW_MODE);
						} catch (final IllegalArgumentException e1) {
							camera.destroy();
							return;
						}
					}
					final SurfaceTexture st = mUVCCameraView.getSurfaceTexture();
					if (st != null) {
						mPreviewSurface = new Surface(st);
						camera.setPreviewDisplay(mPreviewSurface);
//						camera.setFrameCallback(mIFrameCallback, UVCCamera2.PIXEL_FORMAT_RGB565/*UVCCamera2.PIXEL_FORMAT_NV21*/);
						camera.setInspectionFrameCallback(mInspectionFrameCallback);
						camera.startPreview();
					}

					synchronized (mSync) {
						mUVCCamera = camera;
					}
				}
			}, 0);
		}

		@Override
		public void onDisconnect(final UsbDevice device, final UsbControlBlock ctrlBlock) {
			// XXX you should check whether the coming device equal to camera device that currently using
			releaseCamera();
		}

		@Override
		public void onDettach(final UsbDevice device) {
			Toast.makeText(MainActivity.this, "USB_DEVICE_DETACHED", Toast.LENGTH_SHORT).show();
		}

		@Override
		public void onCancel(final UsbDevice device) {
		}
	};

	private synchronized void releaseCamera() {
		synchronized (mSync) {
			if (mUVCCamera != null) {
				try {
					mUVCCamera.setStatusCallback(null);
					mUVCCamera.setButtonCallback(null);
					mUVCCamera.close();
					mUVCCamera.destroy();
				} catch (final Exception e) {
					//
				}
				mUVCCamera = null;
			}
			if (mPreviewSurface != null) {
				mPreviewSurface.release();
				mPreviewSurface = null;
			}
		}
	}

	/**
	 * to access from CameraDialog
	 * @return
	 */
	@Override
	public USBMonitor getUSBMonitor() {
		return mUSBMonitor;
	}

	@Override
	public void onDialogResult(boolean canceled) {
		if (canceled) {
			runOnUiThread(new Runnable() {
				@Override
				public void run() {
					// FIXME
				}
			}, 0);
		}
	}

//	public void writeFile(Context context, byte[] data) {
//		// 获取系统的公共存储路径
//		String publicPath = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).toString();
//		Log.d(TAG, "save to path: " + publicPath);
//
//		// 获取文件路径
//		File file = new File(publicPath + "/image.yuv");
//		try {
//			if (!file.exists()) {
//				file.createNewFile(); // 创建新文件
//			}
//		} catch (IOException e) {
//			e.printStackTrace();
//		}
//
//        // 打开文件输出流
//		try (FileOutputStream fos = new FileOutputStream(file, false)) {
//			// 将字符串转换为字节并写入文件
//			fos.write(data);
//			fos.close();
//		} catch (IOException e) {
//			e.printStackTrace();
//		}
//	}

	private final IInspectionFrameCallback mInspectionFrameCallback = new IInspectionFrameCallback() {
		@Override
		public void onInspectionStart(int totalFrames) {
			Log.d(TAG, "onInspectionStart: total frames=" + totalFrames);
			mTotalInspectionFramesCount = totalFrames;
		}

		@Override
		public void onInspectionStop() {
			Log.d(TAG, "onInspectionStop");
		}

		@Override
		public void onInspectionFrame(ByteBuffer frame, int frameFormat, int frameIndex) {

			Log.d(TAG, "onInspectionFrame: pixelFormat=" + frameFormat + ", frameIndex=" + frameIndex);
			mCurInspectionFrameIndex = frameIndex;
			final String frameInfo = (mCurInspectionFrameIndex+1)+ "/" + mTotalInspectionFramesCount;
			mInspectionFramesText.post(new Runnable() {
				@Override
				public void run() {
					mInspectionFramesText.setText(frameInfo);
				}
			});

			if (frame.remaining() <= 0) {
				Log.e(TAG, "onInspectionFrame: No data!!!");
				return;
			}

			byte[] data = new byte[frame.remaining()];
			frame.get(data, 0, data.length);

//			writeFile(getApplicationContext(), data);

			Log.d(TAG, "onInspectionFrame: frame bytes:" + data.length + ", pixelFormat=" + frameFormat + ", frameIndex=" + frameIndex);

			if (frameFormat == UVCCamera2.FRAME_FORMAT_YUYV) {
				if (data != null && data.length > 0) {
					final byte[] frameData = data;
					mInspectionView.post(new Runnable() {
						@Override
						public void run() {
							Log.d(TAG, "call inspection view's newDataArrived");
							updateYUYVData(frameData);
						}
					});
				}
			}
		}
	};

	// if you need frame data as byte array on Java side, you can use this callback method with UVCCamera2#setFrameCallback
	// if you need to create Bitmap in IFrameCallback, please refer following snippet.

/*	final Bitmap bitmap = Bitmap.createBitmap(UVCCamera2.DEFAULT_PREVIEW_WIDTH, UVCCamera2.DEFAULT_PREVIEW_HEIGHT, Bitmap.Config.RGB_565);
	private final IFrameCallback mIFrameCallback = new IFrameCallback() {
		@Override
		public void onFrame(final ByteBuffer frame) {
			frame.clear();
			synchronized (bitmap) {
				bitmap.copyPixelsFromBuffer(frame);
			}
			mImageView.post(mUpdateImageTask);
		}
	};
	
	private final Runnable mUpdateImageTask = new Runnable() {
		@Override
		public void run() {
			synchronized (bitmap) {
				mImageView.setImageBitmap(bitmap);
			}
		}
	}; */
}
