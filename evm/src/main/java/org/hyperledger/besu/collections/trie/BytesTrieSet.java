/*
 * Copyright contributors to Hyperledger Besu.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package org.hyperledger.besu.collections.trie;

import java.util.AbstractSet;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Deque;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.Objects;

import org.apache.tuweni.bytes.Bytes;

/**
 * A Bytes optimized set that stores values in a trie by byte
 *
 * @param <E> Type of trie
 */
public class BytesTrieSet<E extends Bytes> extends AbstractSet<E> {

  record Node<E extends Bytes>(byte[] leafArray, E leafObject, Node<E>[] children) {

    @Override
    public boolean equals(final Object o) {
      if (this == o) return true;
      if (!(o instanceof Node<?> node)) return false;
      return Arrays.equals(leafArray, node.leafArray)
          && Objects.equals(leafObject, node.leafObject)
          && Arrays.equals(children, node.children);
    }

    @Override
    public int hashCode() {
      int result = Objects.hash(leafObject);
      result = 31 * result + Arrays.hashCode(leafArray);
      result = 31 * result + Arrays.hashCode(children);
      return result;
    }

    @Override
    public String toString() {
      final StringBuilder sb = new StringBuilder("Node{");
      sb.append("leaf=");
      if (leafObject == null) sb.append("null");
      else {
        sb.append('[');
        System.out.println(leafObject.toHexString());
        sb.append(']');
      }
      sb.append(", children=");
      if (children == null) sb.append("null");
      else {
        sb.append('[');
        for (int i = 0; i < children.length; ++i) {
          if (children[i] == null) {
            continue;
          }
          sb.append(i == 0 ? "" : ", ").append(i).append("=").append(children[i]);
        }
        sb.append(']');
      }
      sb.append('}');
      return sb.toString();
    }
  }

  Node<E> root;

  int size = 0;
  final int byteLength;

  /**
   * Create a BytesTrieSet with a fixed length
   *
   * @param byteLength length in bytes of the stored types
   */
  public BytesTrieSet(final int byteLength) {
    this.byteLength = byteLength;
  }

  static class NodeWalker<E extends Bytes> {
    final Node<E> node;
    int lastRead;

    NodeWalker(final Node<E> node) {
      this.node = node;
      this.lastRead = -1;
    }

    NodeWalker<E> nextNodeWalker() {
      if (node.children == null) {
        return null;
      }
      while (lastRead < 255) {
        lastRead++;
        Node<E> child = node.children[lastRead];
        if (child != null) {
          return new NodeWalker<>(child);
        }
      }
      return null;
    }

    E thisNode() {
      return node.leafObject;
    }
  }

  @Override
  public Iterator<E> iterator() {
    var result =
        new Iterator<E>() {
          final Deque<NodeWalker<E>> stack = new ArrayDeque<>();
          E next;
          E last;

          @Override
          public boolean hasNext() {
            return next != null;
          }

          @Override
          public E next() {
            if (next == null) {
              throw new NoSuchElementException();
            }
            last = next;
            advance();
            return last;
          }

          @Override
          public void remove() {
            BytesTrieSet.this.remove(last);
          }

          void advance() {
            while (!stack.isEmpty()) {
              NodeWalker<E> thisStep = stack.peek();
              var nextStep = thisStep.nextNodeWalker();
              if (nextStep == null) {
                stack.pop();
                if (thisStep.thisNode() != null) {
                  next = thisStep.thisNode();
                  return;
                }
              } else {
                stack.push(nextStep);
              }
            }
            next = null;
          }
        };
    if (root != null) {
      result.stack.add(new NodeWalker<>(root));
    }
    result.advance();
    return result;
  }

  @Override
  public int size() {
    return size;
  }

  @Override
  public boolean contains(final Object o) {
    if (!(o instanceof Bytes bytes)) {
      throw new IllegalArgumentException(
          "Expected Bytes, got " + (o == null ? "null" : o.getClass().getName()));
    }
    byte[] array = bytes.toArrayUnsafe();
    if (array.length != byteLength) {
      throw new IllegalArgumentException(
          "Byte array is size " + array.length + " but set is size " + byteLength);
    }
    if (root == null) {
      return false;
    }
    int level = 0;
    Node<E> current = root;
    while (current != null) {
      if (current.leafObject != null) {
        return Arrays.compare(current.leafArray, array) == 0;
      }
      current = current.children[array[level] & 0xff];
      level++;
    }
    return false;
  }

  @Override
  public boolean remove(final Object o) {
    // Two base cases, size==0 and size==1;
    if (!(o instanceof Bytes bytes)) {
      throw new IllegalArgumentException(
          "Expected Bytes, got " + (o == null ? "null" : o.getClass().getName()));
    }
    byte[] array = bytes.toArrayUnsafe();
    if (array.length != byteLength) {
      throw new IllegalArgumentException(
          "Byte array is size " + array.length + " but set is size " + byteLength);
    }
    // Two base cases, size==0 and size==1;
    if (root == null) {
      // size==0 is easy, empty
      return false;
    }
    if (root.leafObject != null) {
      // size==1 just check and possibly remove the root
      if (Arrays.compare(array, root.leafArray) == 0) {
        root = null;
        size--;
        return true;
      } else {
        return false;
      }
    }
    int level = 0;
    Node<E> current = root;
    do {
      int index = array[level] & 0xff;
      Node<E> next = current.children[index];
      if (next == null) {
        return false;
      }
      if (next.leafObject != null) {
        if (Arrays.compare(array, next.leafArray) == 0) {
          // TODO there is no cleanup of empty branches
          current.children[index] = null;
          size--;
          return true;
        } else {
          return false;
        }
      }
      current = next;

      level++;
    } while (true);
  }

  @SuppressWarnings({"unchecked", "rawtypes"})
  @Override
  public boolean add(final E bytes) {
    byte[] array = bytes.toArrayUnsafe();
    if (array.length != byteLength) {
      throw new IllegalArgumentException(
          "Byte array is size " + array.length + " but set is size " + byteLength);
    }
    // Two base cases, size==0 and size==1;
    if (root == null) {
      // size==0 is easy, just add
      root = new Node<>(array, bytes, null);
      size++;
      return true;
    }
    if (root.leafObject != null) {
      // size==1 first check then if no match make it look like n>1
      if (Arrays.compare(array, root.leafArray) == 0) {
        return false;
      }
      Node<E> oldRoot = root;
      root = new Node<>(null, null, new Node[256]);
      root.children[oldRoot.leafArray[0] & 0xff] = oldRoot;
    }
    int level = 0;
    Node<E> current = root;
    do {
      int index = array[level] & 0xff;
      Node<E> next = current.children[index];
      if (next == null) {
        next = new Node<>(array, bytes, null);
        current.children[index] = next;
        size++;
        return true;
      }
      if (next.leafObject != null) {
        if (Arrays.compare(array, next.leafArray) == 0) {
          return false;
        }
        Node<E> newLeaf = new Node<>(null, null, new Node[256]);
        newLeaf.children[next.leafArray[level + 1] & 0xff] = next;
        current.children[index] = newLeaf;
        next = newLeaf;
      }
      level++;

      current = next;

    } while (true);
  }

  @Override
  public void clear() {
    root = null;
  }
}
