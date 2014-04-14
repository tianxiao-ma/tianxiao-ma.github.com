---
title: erlang的bit操作
layout: post
permalink: /2014/04/erlang-bit-syntax
date: Sun Apr 14 16:40:00 pm GMT+8 2014
published: true
---

erlang为解析二进制数据定义了一组语法，下面是摘自官方文档的语法说明：
> <<>>
> <<E1,...,En>>
> 
> Each element Ei specifies a segment of the bit string. Each element Ei is a value, followed by optional size expression and an optional type specifier list.
> 
> Ei = Value |
> Value:Size |
> Value/TypeSpecifierList |
> Value:Size/TypeSpecifierList
> 
> Type= integer | float | binary | bytes | bitstring | bits | utf8 | utf16 | utf32.
> The default is integer. bytes is a shorthand for binary and bits is a shorthand for bitstring. See below for more information about the utf types.
> 
> Signedness= signed | unsigned.
> Only matters for matching and when the type is integer. The default is unsigned.
> 
> Endianness= big | little | native.
> Native-endian means that the endianness will be resolved at load time to be either big-endian or little-endian, depending on what is native for the CPU that the Erlang machine is run on. Endianness only matters when the Type is either integer, utf16, utf32, or float. The default is big.
> 
> Unit= unit:IntegerLiteral.
> The allowed range is 1..256. Defaults to 1 for integer, float and bitstring, and to 8 for binary. No unit specifier must be given for the types utf8, utf16, and utf32.

erlang将一段二进制数据看作一串bit，通过提供特定的语法，允许应用程序对这个bit串进行分段，并为每一段bit定义类型。通过上面的语法，我们可以很方便地解析一段二进制数据。

比如，我们定义了如下的一个二进制数据：

{% highlight erlang linenos %}
Bin = <<125, 254>>.
{% endhighlight %}

`Bin`变量总共有两个字节，是一个长度位16的bit串，通过开头提到的语法，我们可以对这个bit串按照任意的方式进行分段，并指定每一段的类型，比如我们可以将这个串分成长度为4和长度为12的两段：

{% highlight erlang linenos %}
<<A:4, B:12>> = Bin.
{% endhighlight %}

上面的代码利用了`Value:Size`语法，在这种情况下，分段的单位长度是1个bit，也就是说`A:4`表示从整个bit串中截取4个比特，`B:12`表示从整个bit串中截取12个bit。通过`unit:IntegerLiteral`可以修改分段的单位长度，如下所示：

{% highlight erlang linenos %}
<<A:1/unit:4, B:1/unit:4, C:1/unit:4, D:1/unit:4>> = Bin.
{% endhighlight %}

上面的代码将`Bin`分成了4个段，每个段由4个bit组成。需要注意的是，当使用`unit:IntegerLiteral`来指定分段的单位长度时，必须同时指定`Size`。因为erlang是通过`Size * Unit`来确定每一段bit的长度的。

除了可以通过`unit:IntegerLiteral`来指定分段的单位长度之外，通过设定分段的类型也可以改变分段的单位长度，比如：

{% highlight erlang linenos %}
<<A:8/integer, B/binary>> = Bin.
{% endhighlight %}

上面的代码将`Bin`分成了2段，第一段的类型是integer，第二段的类型是binary。由于integer类型的默认单位长度是1，而binary类型的默认单位长度是8，`Bin`就被分成了两个长度都是8的段。

类型除了会影响分段的单位长度之外，还会影响erlang如何翻译某一段bit串。对于上面的代码，在erlang shell中分别打印A和B的值就可以看到，erlang将A看做是一个整形数字输出，而将B看作是一个字符输出。

erlang的二进制解析语法也支持unicode字符集，但是只支持utf8, utf16和utf32这集中编码，看下面的代码,

{% highlight erlang linenos %}
Bin = <<"中文"/utf8>>.
<<Text/utf8>> = Bin. %% 无法正确执行
<<A/utf8, B/utf8>> = Bin.
{% endhighlight %}

要显示地初始化一个二进制变量时，我们可以通过`Bin = << "abc" >>`这样的语法来完成.但是其中"abc"这样的字符串中的字符的unicode代码点必须能够被一个字节表示，如果不满足条件，erlang会执行截取操作，直接丢掉高位的bit。由于中文字符在unicode字符集中的代码点基本都是大于一字节能够表示的范围的，因此我们需要为这样的字符指定编码方案。第一行代码使用utf8来编码中"文字"符串，结果会形成一个包含6个字节的二进制数据串。

上面代码的第二行企图将`Bin`中的数据赋值给`Text`变量，但是执行却失败了，而第3行代码却执行成功了。也就是，我们可以用一个中文字符串(或者unicode字符串)来初始化一个二进制变量，但却无法从二进制变量中获取一个中文字符串，而只能一个字符一个字符地获取，其实也就是获取字符的unicode code point。如果想获取整个字符串需要使用`<<Text/binary>> = Bin`这样的语法。从这里可以看出，utf8(以及utf16和utf32)这个类型在取值的时候表示的是unicode代码点，在赋值的时候表示的是编码方案(将unicode代码点按照不同编码方案转换成一个或者多个字节)。



