package com.serenegiant.usb;

import java.nio.ByteBuffer;

/**
 * Callback interface for UVCCamera class
 * If you need inspection frame data as ByteBuffer, you can use this callback interface
 * with UVCCamera#setInspectionCallback
 */

public interface IInspectionFrameCallback {
    public void onInspectionStart(int totalFrames);
    public void onInspectionStop();
    public void onInspectionFrame(ByteBuffer frame, int frameFormat, int index);
}
