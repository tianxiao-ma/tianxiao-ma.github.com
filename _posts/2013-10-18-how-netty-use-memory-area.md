---
title: 深入Netty-如何管理Memory Arena
layout: post
permalink: /2013/10/how-netty-use-memory-area
date: Fri Oct 18 16:09:56 pm GMT+8 2013
published: true
---

Arena本身是指一块区域，一个场地。在内存管理中，Memory Arena是指内存中的一大块区域。

为了集中管理内存的分配和释放，同时提高分配和释放内存时候的性能，很多框架和应用都会通过预先申请一大块内存，然后通过提供相应的分配和释放接口来使用内存。这样一来，对内存的管理就被集中到几个类或者函数中，由于不再频繁使用系统调用来申请和释放内存，应用或者系统的性能也会大大提高。在这种设计思路下，预先申请的那一大块内存就被称为Memory Arena。

针对Memory Arena的管理有很多不同的方法，下面来看看Netty中是如何组织、管理和使用Memory Arena的。

Netty中的Memory Arena是由多个`Chunk`组成的大块内存区域，而每个`Chunk`则由一个或者多个`Page`组成，因此，对内存的组织和管理也就主要集中在如何管理和组织`Chunk`和`Page`了。

1. Chunk的组织和管理
=========
Chunk主要用来组织和管理大于一个Page的内存分配和释放，在Netty中，Chunk中的Page被构建成一棵满二叉树。假设，一个Chunk由16个Page组成，那么这些Page将会被按照下面的形式组织起来：
<pre>
                                   64
                                    |
                      -----------------------------
                     |                             |
                     32                           32
                     |                             |
                -----------                   -----------
               |           |                 |           |
              16          16                 16          16
               |           |                 |           |
             -----       -----             -----       -----
            |     |     |      |          |     |     |     |
            8     8     8      8          8     8     8     8
            |     |     |      |          |     |     |     |
           ---   ---   ---    ---        ---   ---   ---   ---
          |   | |   | |   |  |   |      |   | |   | |   | |   |
          4   4 4   4 4   4  4   4      4   4 4   4 4   4 4   4
 </pre>
 在上面的这个图中，Page的大小是4，Chunk的大小是64(4 * 16)。整棵树有5层，第一层(也就是叶子节点所在的层)用来分配Page大小的内存，第4层用来分配2个Page大小的内存，依次类推。
 
 每个节点都记录了自己在整个Memory Arena中的偏移地址，当一个节点表示代表的内存区域被分配出去之后，这个节点就会被标记为*已分配*，自这个节点以下的所有节点在后面的内存分配请求中都会被忽略。举例来说，当我们请求一个16字节的存储区域时，上面这个树中的第3层中的4个节点中的一个就会被标记为*已分配*，这就表示整个Memroy Arena中有16个字节被分配出去了，新的分配请求只能从剩下的3个节点及其子树中寻找合适的节点。
 
对树的遍历Netty采用深度优先的算法，但是在选择哪个子节点继续遍历时则是随机的，并不像通常的深度优先算法中那样总是访问左边的子节点。

2. Page的组织和管理
==========
对于小于一个Page的内存，Netty在Page中完成分配。每个Page会被切分成大小相等的多个存储块，存储块的大小由第一次申请的内存大小决定。加上一个Page是4个字节，如果第一次申请的是1个字节，那么这个Page就被分成4个存储块，每个存储块1个字节；如果第一次申请的是2个字节，那么这个Page就被分成2个存储块，每个块2个字节。

一个Page只能用于分配与第一次申请时大小相同的内存，比如，一个4字节的Page，如果第一次分配了1字节的内存，那么后面这个Page只能继续分配1字节的内存，如果有一个申请2字节内存的请求，就需要在一个新的Page中进行分配。

Page中存储区域的分配状态则是通过一个long数组来维护，数组中的每个long的每一位表示一个块存储区域的占用情况，0表示未占用，1表示以占用。对于一个4字节的Page来说，如果这个Page用来分配1个字节的存储区域，那么long数组中就只有一个long类型的数值，这个数值的低4位用来指示各个存储区域的占用情况。对于一个128字节的Page来说，如果这个Page也是用来分配1个字节的存储区域，那么long数组中就会有2个数值，总共128位，每一位代表一个区域的占用情况。

3. 内存回收
======
Netty用来组织和管理Chunk和Page的数据结构，使得归还内存的操作非常简单，低于Chunk来说，只要将相应节点的状态标记位*未使用*即可，而对于Page来说，只要将long数组中相应的位设置成0即可。

4. 相关代码
=====
在`PoolChunk`和`PoolSubPage`两个类中可以看到上述内容的相关代码。注释版本的代码可以<a href="mailto:tianxiao.matx@gmail.com">向我要</a>。

5. 其他信息
=====
本文的内容基于[github上对netty的一个fork](https://github.com/tianxiao-ma/netty)，fork时的版本是4.0
