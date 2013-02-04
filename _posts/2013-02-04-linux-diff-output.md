---
title: 读懂diff命令的输出结果
layout: post
permalink: /2013/02/understand_diff_output/
date: Mon Feb 04 12:30:00 pm GMT+8 2013
published: true
---
diff命令在类unix系统中用来比较两个文件，这个命令有很多的功能，这里以比较两个文本文件为例。为了举例方便，这里给出了两段示例文本，他们的内容如下：

lao文件：
<blockquote>
<pre>
The Way that can be told of is not the eternal Way;
     The name that can be named is not the eternal name.
     The Nameless is the origin of Heaven and Earth;
     The Named is the mother of all things.
     Therefore let there always be non-being,
       so we may see their subtlety,
     And let there always be being,
       so we may see their outcome.
     The two are the same,
     But after they are produced,
       they have different names.
</pre>
</blockquote>

tzu文件：
<blockquote>
<pre>
The Nameless is the origin of Heaven and Earth;
     The named is the mother of all things.
     Therefore let there always be non-being,
       so we may see their subtlety,
     And let there always be being,
       so we may see their outcome.
     The two are the same,
     But after they are produced,
       they have different names.
     They both may be called deep and profound.
     Deeper and more profound,
     The door of all subtleties!
</pre>
</blockquote>

diff命令的输出有三种格式，分别是上下文无关格式、上下文有关格式和统一格式，其中后两种格式都会给出两个文件中不同部分前后的一些信息。下面分别对这三种格式进行说明。

上下文无关格式(Without Context)
---------------
可以通过下面的命令来获得这种格式的输出：
>	diff lao tzu

输出结果如下：
<blockquote>
<pre>
1,4c1,2
< The Way that can be told of is not the eternal Way;
<      The name that can be named is not the eternal name.
<      The Nameless is the origin of Heaven and Earth;
<      The Named is the mother of all things.
---
> The Nameless is the origin of Heaven and Earth;
>      The named is the mother of all things.
11a10,12
>      They both may be called deep and profound.
>      Deeper and more profound,
>      The door of all subtleties!
</pre>
</blockquote>

怎么来解读上面的这段输出结果呢？对于上下文无关的输出，可以用下面的形式化格式进行定义：
<blockquote>
<pre>
change-command
< from-file-line
< from-file-line...
---
> to-file-line
> to-file-line...
</pre>
</blockquote>

其中的*from-file*是指*diff*命令后面跟的第一个文件，*to-file*则是指*diff*命令后面跟的第二个文件。*change-command*有三个，分别是：
<ul>
<li>‘lar’：将*to-file*中的r表示行添加到*from-file*l表示的行后面，这样两个文件的相关部分就能变成一样了。r可以表示一行，也可以表示几行，在表示连续的几行时，格式为start,end。</li>
<li>‘fct’：表示*from-file*中f表示的行与*to-file*中t表示的行有冲突，只要将f表示行替换成t表示的行，那么两个文件的相关部分就能变的一致。f和t可以表示一样，也可以表示连续的多行，在表示多行时，格式为start,end</li>
<li>‘rdl’：表示如果将*from-file*中r表示的行全部删除掉，那么两个文件的相关部分就能够保持一致，而l表示如果这些行出现在*to-file*中，那么应该是在哪一行的后面。</li>
</ul>

了解了*change-command*之后，我们就可以来解读上面的输出了，首先来看*1,4c1,2*这个部分的输出，这表示lan的1到4行与tzu的1到2行有冲突，如果要想两个文件的这个部分变成一样，要么用lan的1到4行替换tzu的1到2行，要么反过来进行替换。

再来看*11a10,12*这个部分的输出，这表示如果将tzu的第10到12行追加到lan的第11行之后，或者将tzu的第10到12行删除掉，两个文件的这个部分就能保持一致。

对于‘rdl’的情况这里没有输出，我们可以举个例子，比如对于*5,7d3*这个输出，就表示如果将lan的第5到7行删除掉，或者将lan的5到7行追加到tzu的第三行之后，那么两个文件的这个部分就可以保持一致，这个与‘lar’其实是类似的。

上下文有关格式(Context Format)
--------------
执行下面的命令可以得到上下文有关的输出格式：
>	diff lao tzu -c

输出的结果如下：
<blockquote>
<pre>
*** lao	Fri Feb  1 12:25:26 2013
--- tzu	Fri Feb  1 12:25:37 2013
***************
*** 1,7 ****
! The Way that can be told of is not the eternal Way;
!      The name that can be named is not the eternal name.
!      The Nameless is the origin of Heaven and Earth;
!      The Named is the mother of all things.
       Therefore let there always be non-being,
         so we may see their subtlety,
       And let there always be being,
--- 1,5 ----
! The Nameless is the origin of Heaven and Earth;
!      The named is the mother of all things.
       Therefore let there always be non-being,
         so we may see their subtlety,
       And let there always be being,
***************
*** 9,11 ****
--- 7,12 ----
       The two are the same,
       But after they are produced,
         they have different names.
+      They both may be called deep and profound.
+      Deeper and more profound,
+      The door of all subtleties!
</pre>
</blockquote>

同样的，我们先来看下形式化的输出结果定义，这种格式的输出首先会给出一个头，这个头的格式如下：
<blockquote>
<pre>
*** from-file from-file-modification-time 
--- to-file to-file-modification time
</pre>
</blockquote>

在头中给出了被比较的两个文件的文件名和最后的修改时间，一般来讲*from-file*是指*diff*命令后面跟的第一个文件，而*to-file*则是该命令后面跟的第二个文件。*from-file*和*to-file*前面的三个星号和三个减号也是有用的，下面会说到。

真正的输出具有如下的格式：
<blockquote>
<pre>
***************
*** from-file-line-numbers ****
		from-file-line
		from-file-line ...
--- to-file-line-numbers ----
		to-file-line 
		to-file-line ...
</pre>
</blockquote>

单独的一连串星号表示两个文件的一个不同点，对应到lan和tzu的输出，我们可以看到这两个文件有两处不同点。之后分别给出了*from-file*和*to-file*各自不同在什么地方，以及应该如何进行变化才可以消除两个文件在这一处的不同。

*from-file-line-numbers*和*to-file-line-numbers*表示不同之处分别在两个文件的哪一行或者哪几行，如果不同点包含多行，那么就会以*start,end*的形式给出，否则只会给出最后一样(可能有点难理解，看过例子之后就明白了)。由于是上下文相关的结果，因此在结果中不仅会包含两个文件中有差异的部分，也会给出这些差异前后的行，这些行中的内容在两个文件中都是一样的。对于这些相同的行，*diff*命令会在这些行的前面加上一个空格原样输出，而对于有差异的行，则会在这些差一行之前放上特殊的符号，这些符号可以是下面三个中的任何一个：

* ‘！’：表示这些行在两个文件中是有差别的，两个文件中有差别的每一行前面都会有这个感叹号；
* ‘+’：表示这些行在另外一个文件中是没有的，应该插入到另外一个文件中；
* ‘-’：表示这些行在另外一个文件中是没有的，应该在当前文件中删除掉；

现在让我们来对照这个形式化的定义来解读一下输出结果，先来看第一个不同点：
<blockquote>
<pre>
*** 1,7 ****
! The Way that can be told of is not the eternal Way;
!      The name that can be named is not the eternal name.
!      The Nameless is the origin of Heaven and Earth;
!      The Named is the mother of all things.
       Therefore let there always be non-being,
         so we may see their subtlety,
       And let there always be being,
--- 1,5 ----
! The Nameless is the origin of Heaven and Earth;
!      The named is the mother of all things.
       Therefore let there always be non-being,
         so we may see their subtlety,
       And let there always be being,
</pre>
</blockquote>

这个结果表示lan中的1到7行与tzu中的1到5行有差异存在。为什么会知道是lan的1到7行与tzu的1到5行有差别，而不是tzu的1到7行与lan的1到5行有差别呢？这是因为在*1,7*这个行范围前面有三个星号，而在输出结果的头部信息信息中，也有这连续的三个星号出现，这样就可以对应起来了，在头信息中跟在三个星号后面的文件就是这里有三个星号开头的行范围所指的文件，三个减号也是一样的作用。

在这个部分的差异中，lan文件的前四行被打了感叹号，而tzu的前两行被打了感叹号，而剩余的行前面则什么符号都没有。对应到前面对符号的说明，我们可以知道，lan的前四行和tzu的前两行内容上是有差异的，但是后面跟的三行却是相同的，如果要是这两个文件的这个部分保持一致，就需要进行替换，要么用lan的前4行替换tzu的前两行，要么反过来进行替换(跟上下文无关输出格式表达的信息是完全一样的)。

再来看第二个不同点：
<blockquote>
<pre>
*** 9,11 ****
--- 7,12 ----
       The two are the same,
       But after they are produced,
         they have different names.
+      They both may be called deep and profound.
+      Deeper and more profound,
+      The door of all subtleties!
</pre>
</blockquote>

这段差异表示lan的9到11行与tzu的7到12行有差异，但是lan部分的差异没有任何内容，而tzu部分给出了6行，其中后三行前面有+号。这说明，需要将tzu部分标了+号的那三行追加到lan文件的第11行之后，或者将tzu中的这三行给删掉，那么这两个文件的这一出差异就可以被消除掉了。对于-号的表示也是类似的。

统一格式(Unified Format)
--------------
最后来看下统一格式，这种格式可能大家平时都有见过，这种格式的输出可以使用下面的命令的到：
>	diff lao tzu -u

输出结果如下：
<blockquote>
<pre>
--- lao	2013-02-01 12:25:26.000000000 +0800
+++ tzu	2013-02-01 12:25:37.000000000 +0800
@@ -1,7 +1,5 @@
-The Way that can be told of is not the eternal Way;
-     The name that can be named is not the eternal name.
-     The Nameless is the origin of Heaven and Earth;
-     The Named is the mother of all things.
+The Nameless is the origin of Heaven and Earth;
+     The named is the mother of all things.
      Therefore let there always be non-being,
        so we may see their subtlety,
      And let there always be being,
@@ -9,3 +7,6 @@
      The two are the same,
      But after they are produced,
        they have different names.
+     They both may be called deep and profound.
+     Deeper and more profound,
+     The door of all subtleties!
</pre>
</blockquote>

这种输出同样包含一个头信息，表达的信息与上下文相关格式的输出结果一样，只是前面的标记略有不同。这种格式下，每一种差异以如下形式表示：
<blockquote>
<pre>
@@ from-file-line-numbers to-file-line-numbers @@ 
line-from-either-file
line-from-either-file 
...
</pre>
</blockquote>

其中*from-file-line-numbers*和*to-file-line-number*的格式是一样的，都是start,count。表示从第几行开始，后面有多少行是存在差异的。如果差异只包含一行那么count部分是没有的，如果差异只存在与一个文件中，那么另外一个文件中只会给出一个结束行，也就是start,count形式变成了end形式(这种形式表示应该把另外一个文件中的行添加到end表示的行后面去)。

这种格式同样定义了几个符号，可以放在每一个差一行的前面，如下：

* +号：表示应该把这些行插入到另外一个文件去；
* -号：表示应该把这些行从当前文件删掉；

让我们来回顾一下开头的输出，这段输出说明，lan和tzu有两个地方存在差异，先来看一下第一个差一点：
<blockquote>
<pre>
@@ -1,7 +1,5 @@
-The Way that can be told of is not the eternal Way;
-     The name that can be named is not the eternal name.
-     The Nameless is the origin of Heaven and Earth;
-     The Named is the mother of all things.
+The Nameless is the origin of Heaven and Earth;
+     The named is the mother of all things.
      Therefore let there always be non-being,
        so we may see their subtlety,
      And let there always be being,
</pre>
</blockquote>

这个输出说明lan从第一行开始的前7行，与tzu从第1行开始前5行是存在差别的，那么要怎么来修正呢？这里假设要修正lan文件，我们看到这段差异的前4行都打上了-号，这样这几行就会从lan文件中被删掉，然后跟着是两行打了+号标记的行，这两行会被插入到lan文件中，插入位置是第一行。在往后的三行则没有任何标记，这就说明这三行是上下文信息，可以忽略。这样两个文件的在这一处的差异就被消除掉了。

再来看另外一个差异，
<blockquote>
<pre>
@@ -9,3 +7,6 @@
      The two are the same,
      But after they are produced,
        they have different names.
+     They both may be called deep and profound.
+     Deeper and more profound,
+     The door of all subtleties!
</pre>
</blockquote>

这个差异表示lan文件第9之后的三行，与tzu第7行之后的6行存在差异，如果要修正，我们就需要定位到lan文件的第9行，然后对照这个差异往后看，由于差异的前3行没有任何标记，因此说明两个文件的这三行是一模一样的，这个时候来到了差异的第4行，lan文件的第13行，由于差异输出的后3行前面都标了+号，说明这3行需要被插入到lan文件中，而插入位置就是第13行。

总结
------------
从上面的介绍可以看出，上下文无关的输出和上下文有关的输出相似度比较大，采用的差异表示法虽然不一样，但是本质是一样的。而统一表示法则有所不同，这种表示法中只有插入和删除操作，而没有替换操作，因此会更加清晰一点，程序处理起来也比较容易。因此，这是目前采用比较多的一种差异表示法。
