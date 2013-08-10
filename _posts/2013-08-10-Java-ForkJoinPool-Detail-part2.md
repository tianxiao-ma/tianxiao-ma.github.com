---
title: ForkJoinWorkerThread中的循环队列实现
layout: post
permalink: /2013/08/forkjoinpoll-datail-part-two
date: Sat Aug 10 20:45:00 pm GMT+8 2013
published: true
---

`ForkJoinWorkerThread`中会有一个队列来保存任务，对这个队列以及其中任务的操作是整个`ForkJoinPool`所采用的"work-stealing"技术的核心部分。这个队列具有如下的特征：

1. 队列是一个可以扩充的循环队列；
2. 队列的大小必须是2的n次方，这样可以用位操作来替代取模的操作(见后续说明)；
3. 有两个变量，`queueTop`和`queueBase`，分别指向队列的头尾，`queueTop`指向下一个任务应该被放入的位置，而`queueBase`则指向下一个可以被"steal"的任务；
4. `queueTop`和`queueBase`总是在增长，每次放入或者被偷去一个任务的时候，他们都会增加1；
5. 队列有最大长度(1 << 24)，当队列增长超过最大长度的时候，会抛出异常；

下面结合代码来说明这个循环队列是如何实现的，下面是`ForkJoinWorkerThread`的`pushTask`方法的源代码：

![pushTask](/images/2013-08/forkjoinpool/push_task.jpeg)

462行代码，实际上使用`queueTop`去跟队列长度-1之后的值做与操作，这个操作起到了取模的作用，当然前提是需要队列长度必须是2的n次方。举例来说，

> 比如队列的长度是4，用2进制表示就是100，那么4-1之后的值是3，2进制表示是11。任何数与11做与操作，得到的数     值必定在00到11之间，也就是10进制的0到3之间，于是就起到了取模的作用了。相反，如果长度不是2的n次方，比如5，其2进制表示是101，减1之后的的二进制是100，任何数跟100与，等到的结果要不就是0，要不就是4，起不到取模的效果。

`u`是队列中的一个偏移地址，这个地址可以用来存放t。`ABASE`是数组对象中实际数据的起始偏移地址，而`ASHIFT`表示要得到数组元素宽度，应该位移的个数。计算这个两个值的代码如下：

![static_initilaizer](/images/2013-08/forkjoinpool/static_initializer.jpeg)

988行通过java的`Unsafe`对象的`arrayBaseOffset`方法得到数据对象中实际数据的起始便宜地址。`ASHIFT`的计算需要两步，首先通过`Unsafe`对象的`arrayIndexScale`方法得到数组元素的宽度，也就是一个数组元素需要占用几个字节，然后再通过995行的代码计算得到`ASHIFT`。`Integer.numberOfLeadingZeros`方法可以得到一个int类型数据的二进制表示中，最高位的1的前面的0的个数，比如对于1来说，它的2进制表示是01，那么在1前面就有31个0，调用`Integer.numberOfLeadingZeros`方法就会返回31。995行代码是为了计算数组元素的宽度是2的多少次方，比如如果数组元素的宽度是4，那么`ASHIFT`就是2，因为4是2的2次方。

有了`ASHIFT`和`ABAS`E我们就可以计算数组中一个元素在数组对象的数据区中的偏移地址了。比如，我们要把一个元素放到数组的下标为2的位置，通过`2 << ASHIFT + ABASE`就可以计算出该元素的偏移地址了。

地463行代码用了`Unsafe`对象的`putOrderedObject`方法(直接操作了内存)，把一个元素直接放到数组对象数据区的指定位置。之后，`queueTop`就被增加了1。

465到468行代码，是实现变长循环队列的关键部分，`s`是未添加任务时的`queueTop`值，s -= queuBase之后，s就表示未添加任务时的有效的队列长度(也可以理解为任务的个数)。467和468行代码的意思就是说，如果任务的个数已经填满了当前的队列，那么就要去增加队列的长度了(调用`growQueue`方法)。

总体上来看，对于`ForkJoinWorkerThread`中的任务队列来说，如果有效任务的个数达不到队列预设的长度，那么队列中的元素槽就会被循环使用，但是当有效任务个数达到了预设的队列长度之后，则会去去扩充队列。但是`queueTop和queueBase`始终都是在增加的。

既然`queueTop`和`queueBase`是始终都在增加的，那么这两个数值就有可能超出int类型的最大表示范围，比如当`queueTop=Integer.MAX_VALUE`之后，如果再有一个任务进来，那么`queueTop`就会变成负数(准确地说是变成了负数的最大值了，也就是`Integer.MIN_VALUE`)。

> `queueTop = Integer.MAX_VALUE + 1 == -2147483648 == Integer.MIN_VALUE`

但是这并不影响相关操作的正确性。为了说明这个问题我们假设队列的长度是4，`queueTop`等于`Integer.MAX_VALUE`。当`queueTop`等于`Integer.MAX_VALUE`时，`queueTop`的2进制形式的所有位都是1，执行`queueTop & (queue.length - 1)`的结果是3，这时任务就被放入`queue[3]`位置，然后`queueTop`被增加1。对于循环队列来说，下一个任务应该被放到`queue[0]`位置，然后是`queue[1]`、`queue[2]`等等，那么实际结果是不是这样的呢？

为了回答这个问题，需要说明一下整型负数的二进制表示形式。`Integer.MIN_VALUE`的二进制表示除了符号位外与正数0的二进制表示相同，`Integer.MIN_VALUE + 1`的二进制表示除了符号位外与正数1的二进制表示形式相同，`Integer.MIN_VALUE + 2`的二进制表示除了符号位外与正数2的表示形式相同，以此类推，直到-1。而-1的二进制表示除了符号位外与正数最大值的二进制表示相同。

有了上面的结论，加上&操作的特点，我们就能知道当`queueTop`等于`Integer.MAX_VALUE`，`queueTop + 1`就会等于`Integer.MIN_VALUE`，而根据之前关于整型负数的二进制表示形式的描述，我们可以知道`Integer.MIN_VALUE & 3`等于0，正是我们想要的结果，而随着`queueTop`的继续增加结果也依然是正确的。


