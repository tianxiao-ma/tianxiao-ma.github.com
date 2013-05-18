---
title: leveldb的Bloom Filter实现
layout: post
permalink: /2013/05/leveldb-bloom-filter-detail/
date: Fri May 17 00:30:00 pm GMT+8 2013
published: true
---
leveldb支持为每一段存储进来的数据提供Bloom Filter，默认情况下是每2M数据生成一个Bloom Filter。算法如下：
{% highlight c++ linenos %}
	// 创建过滤器，根据keys生成过滤器，结果放到dst中
	virtualvoid CreateFilter(constSlice* keys, int n, std::string* dst) const {
    // bits_per_key_表示每个key在过滤器中用多少bit，bits是总共需要的bit数
    size_t bits = n * bits_per_key_;
    // 对于比较小的数据集合，强制总的bit数为64，这样可以保证误差不会太大
    if (bits < 64) bits = 64;

    // 计算需要的字节数，生成的bloom filter会存储在一个字符串中
    size_t bytes = (bits + 7) / 8;
    // 根据需要的字节数重新计算一下需要的bit总数
    bits = bytes * 8;

    const size_t init_size = dst->size();
    // 为过滤器预留足够的空间
    dst->resize(init_size + bytes, 0);
    // dst的末尾，也就是过滤器的末尾放入每个key需要的bit数，也就是过滤的时候需要进行的探测次数
    // 放这个数字的好处还在于相同的代码可以用于生成不同的bloom过滤器，这些过滤器可以有不同的k_值
    dst->push_back(static_cast<char>(k_)); 

	// 下面的代码就是为每个key生成k_个hash值，然后设置过滤器中的相应位
    char* array = &(*dst)[init_size];
    for (size_t i = 0; i < n; i++) {
      uint32_t h = BloomHash(keys[i]);
      const uint32_t delta = (h >> 17) | (h << 15);
      for (size_t j = 0; j < k_; j++) {
        const uint32_t bitpos = h % bits;
        array[bitpos/8] |= (1 << (bitpos % 8));
        h += delta;
      }
    }
    }
{% endhighlight %}
{% highlight c++ linenos %}
	// 判断key是不是在过滤器中
	virtual bool KeyMayMatch(constSlice& key, constSlice& bloom_filter) const {
    constsize_t len = bloom_filter.size();
    if (len < 2) returnfalse;

    const char* array = bloom_filter.data();
    // 最后一个字节里面放了bloom过滤器为每个key使用的bit数
    const size_t bits = (len - 1) * 8;
    // 取最后一个字节中的值
    const size_t k = array[len-1];
    // 根据源码的注释，下面这个判断是预留给新的过滤器编码方式的，用来处理比较短的bloom过滤器
    if (k > 30) {
      return true;
    }

    // 与生成的时候使用相同的hash算法，检查过滤器中相应的位是否被设置过
    uint32_t h = BloomHash(key);
    const uint32_t delta = (h >> 17) | (h << 15);  // Rotate right 17 bits
    for (size_t j = 0; j < k; j++) {
      const uint32_t bitpos = h % bits;
      if ((array[bitpos/8] & (1 << (bitpos % 8))) == 0) returnfalse;
      h += delta;
    }
    return true;
    }
   {% endhighlight %} 
