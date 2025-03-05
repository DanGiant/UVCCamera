//
// Created by ChenJian on 2025/3/3.
//

#ifndef _OBJECT_PTR_LOOP_BUFFER_H_
#define _OBJECT_PTR_LOOP_BUFFER_H_

//#include <iostream>
#include <vector>
#include "ObjectMemory.h"

const uint32_t DEFAULT_MAX_SIZE = 10;

template<class T, ElementDuplicatePtr<T> _Duplicate, ElementDeallocatePtr<T> _Dealloc>
class ObjectPtrLoopBuffer {
public:
    ObjectPtrLoopBuffer(uint32_t maxSize)
        : m_max_size(maxSize)
        , m_count(0)
        , read_pos(0)
        , write_pos(0)
    {
        if (maxSize == 0)
            m_max_size = DEFAULT_MAX_SIZE;
        m_array.resize(m_max_size, nullptr);
    }

    ~ObjectPtrLoopBuffer(){
        reset();
    }

    void reset() {
        // deallocate all elements
        while(m_count > 0) {
            T elem = m_array[read_pos];
            read_pos++;
            if (read_pos >= m_max_size)
                read_pos = 0;
            m_count--;
            if (_Dealloc != nullptr)
                _Dealloc(elem);
        }
        // reset
        m_count = 0;
        read_pos = write_pos = 0;
    }

    void reset(uint32_t maxSize) {
        // deallocate all elements
        while(m_count > 0) {
            T elem = m_array[read_pos];
            read_pos++;
            if (read_pos >= m_max_size)
                read_pos = 0;
            m_count--;
            if (_Dealloc != nullptr)
                _Dealloc(elem);
        }
        // reset
        if (m_max_size != maxSize) {
            m_max_size = maxSize;
            if (m_max_size == 0)
                m_max_size = DEFAULT_MAX_SIZE;
            m_array.resize(m_max_size, nullptr);
        }
        m_count = 0;
        read_pos = write_pos = 0;
    }

    T pop_front() {
        if (m_count > 0) {
            T elem = m_array[read_pos];
            read_pos++;
            if (read_pos >= m_max_size)
                read_pos = 0;
            m_count--;
            return elem;
        }
        return nullptr;
    }

    T push_back(T e) {
        T extrudedElem = nullptr;
        if (e != nullptr) {
            if (m_count == m_max_size) {
                // std::cout << "push an element cause the first one popped!" << std::endl;
                T elemHead = m_array[read_pos];
                read_pos++;
                if (read_pos >= m_max_size)
                    read_pos = 0;
                m_count--;
                extrudedElem = elemHead;
//                if (_Dealloc != nullptr && elemHead != nullptr)
//                    _Dealloc(elemHead);
            }
            m_array[write_pos] = e;
            // maintain write_pos
            write_pos++;
            if (write_pos >= m_max_size)
                write_pos = 0;
            // maintain element m_count
            m_count++;
        }
        return extrudedElem;
    }

    T operator[](uint32_t index) const {
        if (m_count > 0 && index < m_count) {
            index = (read_pos + index) % m_max_size;
            return m_array[index];
        }
        return nullptr;
    }

    ObjectPtrLoopBuffer& operator=(ObjectPtrLoopBuffer& other) {
        if (&other != this) {
            reset(other.m_max_size);
            uint32_t otherCount = other.m_count;
            uint32_t otherReadPos = other.read_pos;
            read_pos = other.read_pos;
            write_pos = other.read_pos;
            m_count = 0;
            for(int i=0; i<otherCount; i++) {
                T srcElem = other.m_array[otherReadPos];
                otherReadPos++;
                if (otherReadPos >= m_max_size)
                    otherReadPos = 0;

                if (srcElem != nullptr && _Duplicate) {
                    T copyElem = _Duplicate(srcElem);
                    m_array[write_pos] = copyElem;
                    write_pos++;
                    if (write_pos >= m_max_size)
                        write_pos = 0;
                    m_count++;
                }
            }
        }
        return *this;
    }

    inline uint32_t max_size() const { return m_max_size; }
    inline uint32_t count() const { return m_count; }

private:
    std::vector<T> m_array;
    uint32_t m_max_size;
    uint32_t m_count;
    uint32_t read_pos;
    uint32_t write_pos;
};

#endif //_OBJECT_PTR_LOOP_BUFFER_H_
