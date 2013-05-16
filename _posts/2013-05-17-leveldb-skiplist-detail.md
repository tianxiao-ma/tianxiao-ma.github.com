---
title: leveldb的SkipList实现
layout: post
permalink: /2013/05/leveldb-skiplist-detail/
date: Fri May 17 00:30:00 pm GMT+8 2013
published: true
---

leveldb的memtable使用了SkipList作为内部的存储结构。SkipList中的每个节点除了数据之外，还会有一个指针列表，这个列表的长度代表了节点在SkipList中所处的层次，列表中的每一个指针指向同一层的先一个元素。比如，一个节点有一个长度为3的指针列表P，那么P[0]指向第0层的下一个元素，P[1]指向第一层的下一个元素，P[2]指向第二层的下一个元素。层次是从0开始往上递增的，最多允许12层。

1. 在现有的SkipList中执行一次查找，在查找过程中将查找路径上每一层被访问的最后一个节点指针保存下来(如果新的节点需要被插入到某一层，那么应该被放到这一层被保留下来的那个节点的后面)；
2. 第一步的返回结果是第0层上某一个节点的指针，这个指针指向被插入的节点在第0层的下一个节点，代码会对返回的指针进行检查，不允许插入重复数据；
3. 随机为新插入的节点分配高度；
4. 利用第一步得到的查询路径信息，将新加入的节点插入到每一层的适当位置；

代码如下：

	Node* prev[kMaxHeight];
	// 执行查找，设置查询路径信息
	Node* x = FindGreaterOrEqual(key, pref);
	
	assert(x == NULL || !Equal(key, x->key));
	
	//获取随机高度
	int height = RandomHeight();
	
	// 如果新节点的高度大于现有最大高度，设置超出部分的查询路径信息
	if (height > GetMaxHeight()) {
	    for (int i = GetMaxHeight(); i < height; i++) {
	      prev[i] = head_;
	    }
	    max_height_.NoBarrier_Store(reinterpret_cast<void*>(height));
	}
	x = NewNode(key, height);
	for (int i = 0; i < height; i++) {
	  x->NoBarrier_SetNext(i, prev[i]->NoBarrier_Next(i));
	  prev[i]->SetNext(i, x);
	
	}
	
上面代码中的kMaxHeight是整个SkipList允许的最大高度，head_是整个SkipList的头节点，这个节点中的指针列表指向每一层的第一个节点。

查询时执行的过程与插入时执行的查找过程一样，将FindGreaterOrEqual的结果直接返回就行。当SkipList中没有找到数据的时候，返回的是SkipList中第一个比要查询的key大的节点。