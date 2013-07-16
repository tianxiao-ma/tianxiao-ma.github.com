---
title: iBatis动态SQL标签中的prepend属性详解
layout: post
permalink: /2013/07/ibatis-prepend-property-detail/
date: Thu Jul 12 23:00:00 pm GMT+8 2013
published: true
---

[iBatis][1]支持在sqlmap中插入动态SQL语句，比较常见的动态SQL标签比如\<dynamic\>、\<isNotNull\>、\<iterate\>等等，通过这些标签我们可以根据输入的参数动态地拼接SQL语句。[iBatis][1]的大部分动态SQL标签都支持一个叫做`prepend`的属性，这个属性可以为由某个动态SQL标签生成的一段SQL语句添加前缀。这篇文章就是分析[iBatis][1]是如何来处理动态SQL标签中的`prepend`属性的。[iBatis in Action][2]这本书中有对各种动态SQL标签及其属性的详细介绍,但是关于`prepend`属性的说明是不是很完整，本文正式对这个点做了补充。

作为一个例子，来看下面的SQL语句，其中除了`key_val`是一定会有的之外，`val1`、`val2`和`val3`都有可能为空：

{% highlight sql linenos %}

	select * from table where key=key_val and (prop1 = val1 or prop2 = val2) and prop3 = val3;

{% endhighlight %}

通过iBatis的动态SQL标签可以写成下面的形式：

{% highlight xml linenos %}

	<select>
		select * from table where
		key=key_val
		<dynamic prepend='and' open="(" close=")">
			<isNotNull property="val1" prepend="or">
				prop1 = #val1#
			</isNotNull>
			<isNotNull property="val2" prepend="or">
				prop2 = #val2#
			</isNotNull>			
		</dynamic>
		<isNotNull property="val3" prepend="and">
			prop3 = #val3#
		</isNotNull>	
	</select>

{% endhighlight %}

简单来说，`prepend`属性的特点就是当动态SQL标签生成了SQL的时候，在所生成的SQL前面追加上`prepend`属性值中的文本，但是并不是所有生成SQL的动态SQL标签都会追加上这个`prepend`的属性值，根据[iBatis in Action][2]书中的说法，会有如下二种情况，导致不会追加`prepend`属性值：

1. 如果动态SQL标签是嵌入在另外一个动态SQL标签内的第一个产生SQL的子标签，并且父标签的`removeFirstPrepend`属性被设置成`true`，那么`prepend`的属性值就不会被追加上去；
2. 如果动态SQL标签是嵌入在一个\<dynamic\>(因为\<dynamic\>标签的removeFirstPrepend属性默认就是`true`)标签内的第一个产生SQL的子标签，并且父标签的`prepend`属性不为空，那么`prepend`的属性值就不会被追加上去；

其实上面的第一条说法是有缺陷的，正确的说发应该是：

>  如果动态SQL标签是嵌入在另外一个动态SQL标签内的第一个产生SQL的子标签，并且父标签的`removeFirstPrepend`属性被设置成`true`，***且`prepend`属性不为空***，那么`prepend`的属性值就不会被追加上去；

看到区别了吗？就是都要满足父标签的`prepend`属性不为空才行，这是为什么呢？请看代码：

{% highlight java linenos %}

	public abstract class BaseTagHandler implements SqlTagHandler {
	
	    public int doStartFragment(SqlTagContext ctx, SqlTag tag, Object parameterObject) {
	        ctx.pushRemoveFirstPrependMarker(tag);
	        return SqlTagHandler.INCLUDE_BODY;
	    }
	
	    public int doEndFragment(SqlTagContext ctx, SqlTag tag, Object parameterObject, StringBuffer bodyContent) {
	        if (tag.isCloseAvailable() && !(tag.getHandler() instanceof IterateTagHandler)) {
	            if (bodyContent.toString().trim().length() > 0) {
	                bodyContent.append(tag.getCloseAttr());
	            }
	        }
	        return SqlTagHandler.INCLUDE_BODY;
	    }
	
	    public void doPrepend(SqlTagContext ctx, SqlTag tag, Object parameterObject, StringBuffer bodyContent) {
	
	        if (tag.isOpenAvailable() && !(tag.getHandler() instanceof IterateTagHandler)) {
	            if (bodyContent.toString().trim().length() > 0) {
	                bodyContent.insert(0, tag.getOpenAttr());
	            }
	        }
	
	        if (tag.isPrependAvailable()) {
	            if (bodyContent.toString().trim().length() > 0) {
	                if (tag.getParent() != null && ctx.peekRemoveFirstPrependMarker(tag)) {
	                    ctx.disableRemoveFirstPrependMarker();
	                } else {
	                    bodyContent.insert(0, tag.getPrependAttr());
	                }
	            }
	        }
	
	    }
	}
	
{% endhighlight %}

上面代码是[iBatis][1]框架中的`BaseTagHandler`类的代码，所有处理动态标签SQL的类都继承自这个类，我们重点来看其中的`doPrepend`方法，注意代码中的第25行：

{% highlight xml %}

	if (tag.isPrependAvailable()) {
	
{% endhighlight %}

从这行代码我们知道，如果动态SQL标签的`prepend`属性不存在，那么`if`语句块是不会执行的，而`if`语句块中的代码正式用来处理是否要追加`prepend`属性值到生成的SQL语句之前的验证逻辑(其中包括对`removeFirstPrepend`属性的验证和处理)。

另外，对于嵌套在其他动态SQL语句内的动态SQL语句，还有一个问题需要注意：

> 为了能让你的`prepend`属性按照你的想法正确执行，应该为每一个子语句的标签添加`prepend`属性。

关于这一点，需要结合[iBatis][1]处理`removeFirstPrepend`属性的方式来说明。简单来说[iBatis][1]是通过堆栈来处理`removeFirstPrepend`属性的。如果没有签到，那么`removeFirstPrepend`属性堆栈中就只会有一个元素，如果有2层嵌套，那么当处理内层动态SQL标签时，`removeFirstPrepend`属性堆栈中就会有2个元素，以此类推，如果有n层签到，那么当处理最内层动态SQL标签时，`removeFirstPrepend`属性堆栈中就会有n个元素。

处理`removeFirstPrepend`属性堆栈需要用到`BaseTagHandler`类的`doPrepend`方法的第27，28行代码，而这两行代码又包含在第25行的`if`语句块内，所以为了能够得到正确的结果，建议在使用动态SQL时最好给所有的标签都加上`prepend`属性，如果不想每个都加，那么至少要给嵌套在其他动态SQL标签内的标签都加上`prepend`属性。当然，如果你对[iBatis][1]关于这部分的代码有全面地了解，这么做就不是必须的了。

*如果想要了解[iBatis][1]是如何处理动态SQL标签的，从`SqlTagHandler`出发，了解其各个子类以及他们的用途相信会是一个非常好的起点。*

[1]: https://code.google.com/p/mybatis/
[2]: http://www.amazon.com/iBatis-Action-Clinton-Begin/dp/1932394826/