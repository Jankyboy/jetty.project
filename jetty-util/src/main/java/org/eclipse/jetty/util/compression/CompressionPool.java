//
// ========================================================================
// Copyright (c) 1995-2020 Mort Bay Consulting Pty Ltd and others.
//
// This program and the accompanying materials are made available under
// the terms of the Eclipse Public License 2.0 which is available at
// https://www.eclipse.org/legal/epl-2.0
//
// This Source Code may also be made available under the following
// Secondary Licenses when the conditions for such availability set
// forth in the Eclipse Public License, v. 2.0 are satisfied:
// the Apache License v2.0 which is available at
// https://www.apache.org/licenses/LICENSE-2.0
//
// SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
// ========================================================================
//

package org.eclipse.jetty.util.compression;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;

import org.eclipse.jetty.util.component.AbstractLifeCycle;

public abstract class CompressionPool<T> extends AbstractLifeCycle
{
    public static final int INFINITE_CAPACITY = -1;

    private final Queue<T> _pool;
    private final AtomicInteger _numObjects = new AtomicInteger(0);
    private int _capacity;

    /**
     * Create a Pool of {@link T} instances.
     *
     * If given a capacity equal to zero the Objects will not be pooled
     * and will be created on acquire and ended on release.
     * If given a negative capacity equal to zero there will be no size restrictions on the Pool
     *
     * @param capacity maximum number of Objects which can be contained in the pool
     */
    public CompressionPool(int capacity)
    {
        _capacity = capacity;
        _pool = new ConcurrentLinkedQueue<>();
    }

    public int getCapacity()
    {
        return _capacity;
    }

    public void setCapacity(int capacity)
    {
        _capacity = capacity;
    }

    protected abstract T newObject();

    protected abstract void end(T object);

    protected abstract void reset(T object);

    /**
     * @return Object taken from the pool if it is not empty or a newly created Object
     */
    public T acquire()
    {
        T object;

        if (_capacity == 0)
            object = newObject();
        else
        {
            object = _pool.poll();
            if (object == null)
                object = newObject();
            else if (_capacity > 0)
                _numObjects.decrementAndGet();
        }

        return object;
    }

    /**
     * @param object returns this Object to the pool or calls {@link #end(Object)} if the pool is full.
     */
    public void release(T object)
    {
        if (object == null)
            return;

        if (_capacity == 0 || !isRunning())
        {
            end(object);
        }
        else if (_capacity < 0)
        {
            reset(object);
            _pool.add(object);
        }
        else
        {
            while (true)
            {
                int d = _numObjects.get();

                if (d >= _capacity)
                {
                    end(object);
                    break;
                }

                if (_numObjects.compareAndSet(d, d + 1))
                {
                    reset(object);
                    _pool.add(object);
                    break;
                }
            }
        }
    }

    @Override
    public void doStop()
    {
        T t = _pool.poll();
        while (t != null)
        {
            end(t);
            t = _pool.poll();
        }
        _numObjects.set(0);
    }

    @Override
    public String toString()
    {
        return String.format("%s@%x{%s,size=%d,capacity=%s}",
            getClass().getSimpleName(),
            hashCode(),
            getState(),
            _pool.size(),
            _capacity < 0 ? "UNLIMITED" : _capacity);
    }
}
