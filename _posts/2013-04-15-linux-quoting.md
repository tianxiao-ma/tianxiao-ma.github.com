---
title: User newline in sed on mac os
layout: post
permalink: /2013/04/how-to-use-newline-in-sed-on-mac-os/
date: Wed Apr 15 18:30:00 pm GMT+8 2013
published: true
---

# MAC sed 替换命令中使用换行符
问题的起因来自于在Mac上使用sed将文本中的空格替换成换行符，使用的命令如下：

`sed  -e 's/ /\n/g' file`

使用的文本如下，保存在一个文件file中：

`foo bar baz quux`

对file文件使用上面的sed命令得到的结果是：

*`foonbarnbaznquux`*

显然不是预期的结果。但是同样的命令使用gnu的sed执行可以得到正确的结果(Mac的系统基于BSD版本的linux，很多内置的工具都是BSD版本的)。`man sed`了一下，发现BSD版本的`sed`命令说明里面有下面这段话：

> A line can be split by substituting a newline character into it.  To specify a newline character in the replacement string, precede it with a backslash.

基本就是说可以在原来的行中插入换行符，但是要在换行符之前加一个反斜杠，变成命令就是下面这个样子：

`sed  -e 's/ /\\\n/g' file` (其中的\\\n是必要的，如果是\\n这种形式，那么\\部分会被替换成一个反斜杠，n原样保留，这个是linux的[quoting机制](https://www.gnu.org/software/bash/manual/bashref.html#Quoting)。)

执行之后，发现结果是下面这个样子的：

*`foo\nbar\nbaz\nquux`*

还是不对啊，于是只能继续看Shell中关于Quoting的文档了，其中果然有一节相关的内容，叫做[ANSI-C Quoting](https://www.gnu.org/software/bash/manual/bashref.html#ANSI_002dC-Quoting)。其中介绍了一中`$’string’`形式的表达方式，如果在单引号扩起来的字符串之前加上一个`$`符号，那么后面字符串中的C格式的转义字符会被解析成恰当的格式，比如`\n`会被解释成换行符、`\t`会被解释成制表符等等。将命令修改一下变成下面的格式：

`sed  -e 's/ /'$'\\\n/g' file`

这样就可以得到正确的结果了，这种替换的方式目前只在Mac上的BSD 版本的`sed`测试过，其他版本的linux上的BSD版本的`sed`没有测试过，未必生效。

参考文献：  
[1]: [Linux Shell Quoting Mechanism](https://www.gnu.org/software/bash/manual/bashref.html#Quoting)




