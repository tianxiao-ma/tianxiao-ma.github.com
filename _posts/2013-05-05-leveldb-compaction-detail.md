---
title: LevelDB合并算法详解
layout: post
permalink: /2013/05/leveldb-compaction-detail/
date: Sun May 25 21:15:00 pm GMT+8 2013
published: true
---

在`DBImpl`的`MakeRoomForWrite`方法中会进行判断，其中有下面几个分支：

* 如果level0的文件数接近需要合并的数量了，那么当前线程会等待1毫秒,放缓一下写入的速度，如果写如太快，可能会导致需要比较长的时间来压缩level0上的所有文件，所以宁可每个线程都慢一点，这样压缩的时候会快一点；
* 如果memtable中还有足够的空间，方法就返回了，默认情况下一个memtable的大小是4M；
* 如果imm_不为NULL，说明memtable中已经没有空间了，但是之前的那个memtable还没有合并完成(这个就是写入过快的表现)，这个时候就让当前线程等待；
* 如果level0上的文件太多了，当前线程就会被阻塞住，不再允许写入，leveldb中的配置是12，当level0中的文件数>=12个的时候，就拒绝写入了，当level0的文件被合并到level1上之后，才会重新开放写入；
* 如果前面的分支都没有执行，说明memtable已经满了，这个时候会将现有的memtable设置成冻结状态，然后新开辟一个memtable供写入，同时会调度一个合并操作，将上一个被写满的memtable合并到level0上过去；

合并操作从调用DBImpl类的`MaybeScheduleCompaction`方法开始，这个方法中会进行一下条件的检查，如果当前没有正在执行的合并并且确实需要进行合并，那么启动一个后台线程去执行`DBImpl`中的`BGWork`方法。

`BGWork`方法中调用`DBImpl的BackgroundCall`，而这个方法回去调用`DBImpl`的`BackgroundCompaction`方法去执行具体的合并逻辑，`BackgroundCompaction`方法执行完成之后，会再去调用一下`MaybeScheduleCompaction`方法，这样做是的好处是当一次合并之后，如果某一个level下的文件太多了，那么马上可以再进行一次合并。这样下来整个合并的过程就是一个大的循环，直到没有一个level需要进行合并为止。

`BackgroundCompaction`会检查是否有冻结的meltable需要写入到level0，如果有，则将冻结的meltable写入到level0中去，写入完成之后会调用`VersionSet`的`LogAndApply`方法，将变更同步到当前的`Version`中，完成之后直接返回，不执行后面的逻辑。如果没有冻结的麽麽他不了，则会判断是否存在manual_compaction，如果存在则将manual_compaction转换成一个`Compaction`对象，如果不存在则会调用`VersionSet`的`PickCompaction`方法来选择合适的合并返回来生成`Compaction`对象。得到`Compaction`对象之后，`BackgroundCompaction`方法会根据一些条件执行如下两个操作：

* 如果不是manual_compaction并且被合并的level上只有一个文件，而被合并的level的下一层，也就是level+1层上没有需要合并的文件，那么就简单地将level层上需要被合并的那个文件移到level+1层上；
* 如果不是上面的情况，那么会调用 `DoCompactionWork`方法来将多个文件进行合并，然后放到level+1层上；

合并操作的最后都会调用`VersionSet`的`LogAndApply`方法，将变动的结果保存下来，生成新的当前`Version`，在`VersionSet`的`LogAndApply`方法中，会计算出下一次合并应该发生在哪一层以及这一层的compaction_score是多少(通过调用`VersionSet`的`Finalize`方法进而设置`Version`的compaction_level和compaction_score完成)。

leveldb保证除了level0之外，其他level中的文件不会出现key覆盖的情况。在合并的时候，由于level0中的文件可能存在key覆盖，所以需要选择其中的一个key写入level1。leveldb的做法是为每个key都生成一个对应的序列号，在排序的时候，值相同的key是按照序列号降序排列的。所以，在合并level0层中的文件时，值相同的一组key，其中序列号最大的那个key会被保存下来写入level1，而其他的key则会被丢弃，相当于被序列号更大的相同的key覆盖掉了。这就相当于leveldb需要保证写入操作的时序性，后写入的key的序列号一定要比先写入的key的序列号大，所以，leveldb在写入是使用了FIFO缓冲队列。

对于删除操作leveldb也是在合并的时候进行处理的，同样是在`DoCompactionWork`方法中。在执行合并的过程中，如果发现一个key需要被删除，同时这个key在level+2层以及更高的层里面没有，那么这个key以及具有相同值的key都会被删除掉，不写入level+1层中。如果这个key在level+2层或者更高的层中出现过，那么这个key就不能被删除，要重新写入level+1层。这是因为，合并操作是将level和level+1上的文件进行合并，所以一个被删除的key如果在level+2或者更高的层里面出现过，如果我们删除了这个key，那么在level+2或者更高的层中仍然能够读到，这样就会得到错误的结果。所以，需要将这个key保留下来，写入到level+1层中，这样当level+1层中文件需要合并的时候，如果这个key出现在level+2层，那个时候这个key会被删除掉。如果出现在更高的层中，那么就要等到后面合并level+2或者更高的层中的文件时才会被删除了。还有一种删除操作的情况，那就是一个key被删除之后，重新被写入了。这种情况下，执行正常的合并逻辑就可以了，因为合并的时候序列号最大的那个key会被保留，其他相同的key都会被删除的。

由于leveldb保证写入的时序性，对于相同的key来说，在同一层中序列号大的key一定是在序列号小的key之后写入的，在不同层中，存在与越高层中的key的序列号越小。因此，查找的时候，只要返回第一个匹配的key就可以了。