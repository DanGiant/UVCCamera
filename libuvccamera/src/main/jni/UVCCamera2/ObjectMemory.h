//
// Created by ChenJian on 2025/3/3.
//

#ifndef _OBJECT_MEMORY_H
#define _OBJECT_MEMORY_H

#include <stddef.h>

template <class T>
using ElementAllocatePtr = T (*)(size_t elemDataSize);

template <class T>
using ElementDeallocatePtr = void (*)(T elem);

template <class T>
using ElementCopyPtr = void (*)(T dst, T src);

template <class T>
using ElementDuplicatePtr = T (*)(T src);

#endif //_OBJECT_MEMORY_H
