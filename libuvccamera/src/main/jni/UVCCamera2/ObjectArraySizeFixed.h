//
// Created by ChenJian on 2025/3/2.
//

#ifndef _OBJECT_ARRAY_SIZE_FIXED_H_
#define _OBJECT_ARRAY_SIZE_FIXED_H_

// #include "utilbase.h"
#include <list>
#include "ObjectMemory.h"

template <class T, ElementAllocatePtr<T> _Alloc, ElementDeallocatePtr<T> _Dealloc>
class ObjectArraySizeFixed {
private:
	std::list<T> m_elements;
    uint32_t m_maxElemNum;
    uint32_t m_elemMaxDataSize;

public:
	explicit ObjectArraySizeFixed(uint32_t maxElemNum,
                                  size_t elemMaxDataSize)
        : m_maxElemNum(maxElemNum)
        , m_elemMaxDataSize(elemMaxDataSize)
    {
        if (maxElemNum > 0 && elemMaxDataSize > 0) {
            for(int i=0; i<maxElemNum; i++) {
                if (_Alloc != nullptr) {
                    T e = _Alloc(elemMaxDataSize);
                    m_elements.push_back(e);
                }
            }
        }
    }

	~ObjectArraySizeFixed() {
        clear();
    }

    inline bool empty() const { return m_elements.empty(); }
	inline int size() const { return m_elements.size(); }
	inline int capacity() const { return m_elements.max_size(); }

    /**
     * push T to the list tail
     */
    void push_back(const T elem) {
		m_elements.push_back(elem);
	}

	/**
	 * remove T from the list head
	 */
	T pop_front() {
        T e = m_elements.front();
        m_elements.pop_front();
		return e;
	}

	/**
	 * clear the T list
	 */
	inline void clear() {
		while(m_elements.size() > 0) {
            T e = m_elements.front();
            m_elements.pop_front();
            if (_Dealloc != nullptr)
                _Dealloc(e);
        }
	}
};

#endif	//_OBJECT_ARRAY_SIZE_FIXED_H_
