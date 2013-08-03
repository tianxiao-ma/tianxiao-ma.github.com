---
title: How to Use Operators of Linux find Command
layout: post
permalink: /2013/04/how-to-use-operators-of-linux-find-command/
date: Wed Apr 18 16:21:00 pm GMT+8 2013
published: true
---

linux的find命令用来查询磁盘上满足条件的文件或者满足条件的路径。这里对其基本的使用方式不进行讨论，主要讨论find命令提供的操作符，主要目的是说明白这些操作符是怎么样工作的，会列举1，2个例子来说明。明白了例子中的操作符的工作方式之后，其他的操作符的工作方式也是一样的。关于find的详细文档可用通过 `man find` 来查看机器上的find命令的帮助文档。本文的内容对于GNU版本的find和BSD版本的find都是适用的。

find命令的基本格式如下：

> *find [options] [path]{1,n} expression [operator expression]*

命令后面可以跟多个选项，然后是跟一个或者多个path，find命令将在这些路径下面查找符合要求的文件或者路径。path之后就是表达式了，这些表达式可以被理解成判断条件，当表达式的值为真时，碰到的路径或者文件就会被find命令打印到标准输出上面。多个表达式之间可以通过操作符相互连接。

find提供的表达式有多种，包括与、或、非等等，具体可以看一下帮助文档。这里说一下比较常用的与和或。关于与和或的说明如下，摘自BSD版本的find命令帮助文档：

>expression -and expression  expression expression: The -and operator is the logical AND operator.  As it is implied by the juxtaposition of two expressions it does not have to be specified.  The expression evaluates to true if both expressions are true. The second expression is not evaluated if the first expression is false. 

> expression -or expression: The -or operator is the logical OR operator.  The expression evaluates to true if either the first or the second expression is true.  The second expression is not evaluated if the first expression is true.

其中与操作符用-and表示，两个表达式之间如果没有任何操作符那么默认就是与的关系，另外-a可以用来表示与操作符。只有当两个表达式都返回true的情况下，find命令才会打印当前路径或者文件。而且，如果第一个表达式返回了false，那么第二个表达式是不会被检查的。

或操作符用-or表示，也可以用-o标识。两个表达式中，只要有一个表达式返回true，那么find命令就会打印当前的路径或者文件。而且，如果第一个表达式返回true，第二个表达式就不会被执行，只有当第一个表达式返回false的情况下，第二个表达式才会被执行。

另外还要提一下圆括号操作符，官方的说明如下，**注意表达式和两端的括号之间需要有空格**：

> ( expression ): This evaluates to true if the parenthesized expression evaluates to true.

意思就是说，被括号扩起来的整个表达式的真假与括号里面的表达式的真假是一致的，它的作用与c语言中的括号的作用是一样的，用来改变优先级。通过括号我们可以这么写find的查询条件：

> ( e1 -o e2 -o e3 ) -a e4

这个表达的意思是如果e1、e2和e3中有一个为true并且e4为true那么整个表达式的返回值就会是true。由于find命令的与操作符的优先级要比或操作符的优先级高，因此如果没有括号的话，表达式的意思就会变成e1为true、e2为true或者e3和e4同时为true这三种情况下，整个表达式的值为true。

下面来看个例子，在看例子之前先来看一个比较特殊但是很有用的find表达式`-prune`。官方的说明如下：

> This primary always evaluates to true.  It causes find to not descend into the current file.  Note, the -prune primary has no effect if the -d option was specified.

这个表达式总是会返回true，但是它有一个副作用，就是让find命令放弃当前文件。这里说的文件也可以是目录，如果是目录，那么find命令就不会检查这个目录下面的所有子目录和文件。另外，当-d选项被打开的时候，-prune表达式的副作用就会消失。

这个表达式的用处在于，在很多情况下，我们使用find命令时是希望能够排除某些目录或者文件的。比如，在一个Java工程中，我们希望列出所有不是单元测试的源代码文件，使用`-prune`表达式就可以非常轻松的完成：

> find /you/project/path -name '*Test*.java' -prune -o -name '*.java' -print

这里用了很多的默认特性，完整且比较清晰的写法应该是这样的：

> find /you/project/path \\( -name '*Test*.java'  -and -prune \\) -o \\( -name '*.java' -and -print \\)

由于左右圆括号在shell中有特殊含义，所以需要进行转义。通过之前的介绍这个表达式应该不难理解，已经很直观了。这里有一点需要说明一下，就是`-print`操作符。这个操作符与`-prune`一样，总是返回true，其副作用就是让find命令打印当前的文件或者路径。很多人可能会说，自己在用find的时候也没有用这个参数啊，find不也是会打印的吗？对，在默认的情况下，find总是会为我们添加上一个-print参数，如果在使用find的时候没有显示的输入任何会导致输出的表达式，那么find就会为我们添加一个`-print`表达式，如果你的命令是`find /you/path -name *.java`，那么真正执行的时候就会变成`find /you/path \\( -name *.java \\) -a -print`。如果我们上面的命令没有在第二个括号中添加`-print`操作符，那么真正的命令就会变成下面这样：

> find /you/project/path \\( \\( -name '*Test*.java'  -and -prune \\) -o \\( -name '*.java' \\) \\) -and -print

意思已经完全变了，会打印出全部的java文件，就像我们使用`find /you/path -name *.java`一样(至于为什么，前面已经说过了)。
