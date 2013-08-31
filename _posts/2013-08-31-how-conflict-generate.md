---
title: 版本管理系统中的冲突是怎样产生的-以git为例
layout: post
permalink: /2013/08/how-conflict-generate
date: Sat Aug 31 09:15:00 pm GMT+8 2013
published: true
---


我们在使用版本管理系统的时候，比如git、SVN或者CVS等等，在执行合并操作的时候经常会碰到版本管理工具提示合并某个文件的时候发生冲突，要求我们去手动处理这些冲突的情况。那么，为什么会出现冲突呢？下面我们以[git](http://git-scm.com)这个目前最流行的版本管理系统为例来家以说明。

首先，创建一个用来进行测试用的git代码库，执行下面的命令：

{% highlight bash %}
mkdir git-conflict;
cd git-conflict;
git init;
{% endhighlight %}

然后，我们在这个空的代码库中创建一个叫做`readme`的文本文件，其内容如下：
> Git is a free and open source distributed version control system designed to handle everything from small to very large projects with speed and efficiency. 
> 
> Git is easy to learn and has a tiny footprint with lightning fast performance. It outclasses SCM tools like Subversion, CVS, Perforce, and ClearCase with features like cheap local branching, convenient staging areas, and multiple workflows. 

并通过下面的命令将这个文件提交到当前分支：

{% highlight bash %}
git commit -a -m 'add readme'
{% endhighlight %}

为了制造冲突，我们还需要创建两个分支，通过下面的命令可以创建分支：

{% highlight bash %}
git branch branchA;
git branch branchB;
{% endhighlight %}

到目前为止，我们的代码库中分支情况如下所示：

{% highlight bash %}
  branchA
  branchB
* master
{% endhighlight %}

一共有3个分支，我们目前正处在`master`分支上，`branchA`和`branchB`是我们刚才创建的分支。现在我们要修改`branchA`和`branchB`两个分支中的`readme`文件，然后在`master`分支上分别去合并这两个文件，看看会发生什么事情。

{% highlight bash %}
git checkout branchA;
vi readme;
{% endhighlight %}

`branchA`中的readme修改之后的结果如下，修改的部分用红色表示：
> Git is a `free, open` source distributed version control system designed to handle everything from small to very large projects with speed and efficiency. 
> 
> Git is easy to learn and has a tiny footprint with lightning fast performance. It outclasses SCM tools like Subversion, CVS, Perforce, and ClearCase with features like cheap local branching, convenient staging areas, and multiple workflows. 

{% highlight bash %}
git checkout branchB;
vi readme;
{% endhighlight %}

`branchB`中的readme修改之后的结果如下，修改的部分用红色表示：

> Git is a free and open source distributed version control system designed to handle `anything` from small to very large projects with speed and efficiency. 
> 
> Git is easy to learn and has a tiny footprint with lightning fast performance. It outclasses SCM tools like Subversion, CVS, Perforce, and ClearCase with features like cheap local branching, convenient staging areas, and multiple workflows. 

将`branchA`和`branchB`的修改都提交之后，我们切换到`master`分支，然后开始进行合并，首先来合并`branchA`：

{% highlight bash %}
git merge branchA;
Updating 94acde9..e13b347
Fast-forward
 readme | 2 +-
 1 file changed, 1 insertion(+), 1 deletion(-)
{% endhighlight %}

可以看到合并顺利完成，git通过一次`Fast-forward`完成了这次合并操作，同时还给出了合并操作的详细信息，`1 file changed, 1 insertion(+), 1 deletion(-)`，就是说有一个文件被修改了，插入了一行，删除了一行。

我们继续来合并`branchB`：

{% highlight bash %}
git merge branchB；
Auto-merging readme
CONFLICT (content): Merge conflict in readme
Automatic merge failed; fix conflicts and then commit the result.
{% endhighlight %}

这次问题来了，git告诉我们由于内容的冲突，合并失败了。然我们打开发生合并冲突的`readme`文件看一下：

{% highlight bash %}
cat readme;

<<<<<<< HEAD
Git is a free, open source distributed version control system designed to handle everything from small to very large projects with speed and efficiency. 
=======
Git is a free and open source distributed version control system designed to handle anything from small to very large projects with speed and efficiency. 
>>>>>>> branchB

Git is easy to learn and has a tiny footprint with lightning fast performance. It outclasses SCM tools like Subversion, CVS, Perforce, and ClearCase with features like cheap local branching, convenient staging areas, and multiple workflows.
{% endhighlight %}

可以看到，在文件的第一行出现了冲突，在冲突文件中可以看到`master`分支中这一行的内容和`branchB`中这一行的内容。出现冲突的原因是在`branchA`和`branchB`中都对`readme`文件的同一行进行了修改，因此git无法确定到底应该应用哪一个分支上的修改。

那么git是怎么检测到这个冲突的呢？这一点要从如何执行合并说起，简单来说，合并过程其实就是git帮我们完成了linux系统中的`diff`和`patch`命令。以本文的中的例子来说，当我们执行`git merge branchB`命令的时候，git会根据`brancheB`的最新提交和上一次提交之间的变化信息生成一个diff文件，内容大概是下面这个样子：

{% highlight bash %}
diff --git a/readme b/readme
index 6f93989..51aa6a0 100644
--- a/readme
+++ b/readme
@@ -1,3 +1,3 @@
-Git is a free and open source distributed version control system designed to handle everything from small to very large pr
+Git is a free and open source distributed version control system designed to handle anything from small to very large proj
 
 Git is easy to learn and has a tiny footprint with lightning fast performance. It outclasses SCM tools like Subversion, CV
{% endhighlight %}

根据这个diff信息，git就可以知道要修改`readme`文件的哪一行，以及如何修改。上面的diff信息的意思就是说，要删除原来的`readme`文件中的第一行，然后在第一行的位置插入新的一行。如果了解`diff`和`patch`命令的话，应该知道`patch`命令在应用修改信息的时候是需要全文匹配的，也就是说被删除的行必须在位置和内容上与diff文件中保存的信息一模一样。其实，git也是这么处理的。

由于`branchB`中对`readme`文件的修改是基于最原始的`master`分支中的`readme`文件进行的，所以diff信息也是据此产生的。由于我们在合并`branchB`之前，合并了`branchA`，而`branchA`正好也修改了`readme`文件的第一行内容，这就导致git在应用由`branchB`所产生的diff信息时，发现当前`readme`文件的第一行(也就是从`branchA`那里合并过来的修改)，与diff信息中要删除的那一行的内容不匹配，此时git就会提示合并冲突。如果在`branchA`中修改的是第二行，那么在合并`branchB`的时候就不会有冲突发生(不信？可以自己试试)。

如果我们在一个分支上执行了很多次的提交，在执行合并的时候，git也会帮我们相应地创建多个diff文件，比如在创建分支是，这个分支的最新提交是A，后来我们在这个分支上进行了3次提交，分别是B、C和D，那么在其他分支合并这个分支的时候，git会为我们创建3个diff文件，其中第一个diff文件保存了提供B与提交A之间的文件差别信息，第二个diff文件保存了提交C和提交B之间的文件差别信息，第三个diff文件则保存了提交D与提交C之间的差别信息。然后git会依次将这个3个diff文件应用到执行合并的分支上去。在应用任何一个diff文件时，如果发现diff文件中保存的修改信息与被修改文件的内容对不上，就会提示冲突。

其实我们自己完全可以通过`diff`和`patch`命令来完成这个合并的过程(在每个提交点都使用`diff`命令创建diff文件)，但是由于git中保存了所有的历史信息，因此git可以很方便的帮我们完成这个操作。

其他的版本管理系统在执行合并操作的时候也会采用一样的方式。
