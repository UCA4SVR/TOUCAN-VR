/* Copyright 2017 Laboratoire I3S, CNRS, Université côte d'azur
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */
package fr.unice.i3s.uca4svr.toucan_vr.mediaplayer.upstream;

import com.google.android.exoplayer2.upstream.Allocation;
import com.google.android.exoplayer2.upstream.Allocator;
import com.google.android.exoplayer2.util.Assertions;

import java.util.ArrayList;

/**
 * An {@link Allocator} with unbounded memory allocation.
 */
public final class UnboundedAllocator implements Allocator {

  private final ArrayList<Allocation> allocations = new ArrayList();

  private final boolean trimOnReset;

  /**
   * Constructs an instance without creating any {@link Allocation}s up front.
   *
   * @param trimOnReset Whether memory is freed when the allocator is reset. Should be true unless
   *     the allocator will be re-used by multiple player instances.
   * @param individualAllocationSize The length of each individual {@link Allocation}.
   */
  public UnboundedAllocator(boolean trimOnReset, int individualAllocationSize) {
    this(trimOnReset, individualAllocationSize, 0);
  }

  /**
   * Constructs an instance with some {@link Allocation}s created up front.
   * <p>
   * Note: {@link Allocation}s created up front will never be discarded by {@link #trim()}.
   *
   * @param trimOnReset Whether memory is freed when the allocator is reset. Should be true unless
   *     the allocator will be re-used by multiple player instances.
   * @param individualAllocationSize The length of each individual {@link Allocation}.
   * @param initialAllocationCount The number of allocations to create up front.
   */
  public UnboundedAllocator(boolean trimOnReset, int individualAllocationSize,
                            int initialAllocationCount) {
    Assertions.checkArgument(individualAllocationSize > 0);
    Assertions.checkArgument(initialAllocationCount >= 0);
    this.trimOnReset = trimOnReset;
  }

  public synchronized void reset() {
    if (trimOnReset) {
      allocations.clear();
    }
  }

  /**
   * Adds an allocation of the required length to the allocations list.
   * Release the allocation when it is not needed anymore.
   * @param length
   *  The length of the newly created allocation
   * @return
   *  The newly created allocation after its addition to the allocations list
   */
  public synchronized Allocation allocate(int length) {
    byte[] allocatedSpace = new byte[length];
    Allocation allocation = new Allocation(allocatedSpace, 0);
    allocations.add(allocation);
    return allocation;
  }

  @Override
  public synchronized Allocation allocate() {
    throw new RuntimeException("method stub, need implementation");
  }

  @Override
  public synchronized void release(Allocation allocation) {
    allocations.remove(allocation);
  }

  @Override
  public synchronized void release(Allocation[] allocations) {
    for (Allocation allocation : allocations) {
      // Weak sanity check that the allocation probably originated from this pool.
      this.allocations.remove(allocation);
    }
    // Wake up threads waiting for the allocated size to drop.
    notifyAll();
  }

  @Override
  public synchronized void trim() {
    // Nothing to do there.
    // The queue grows and shrinks as allocation are written and read.
    // The queue has always the exact needed size.
  }

  @Override
  public synchronized int getTotalBytesAllocated() {
    // Only method of the interface called outside the TrackOutput, thus making it entirely correct.
    int allocatedSize = 0;
    for (Allocation allocation : allocations) {
      allocatedSize += allocation.data.length;
    }
    return allocatedSize;
  }

  @Override
  public int getIndividualAllocationLength() {
    // The individual size depends on the size of the content.
    // One individual allocation holds one piece of content and is not of fixed size.
    return -1;
  }

  public void setTargetBufferSize(int targetBufferSize) {

  }
}
