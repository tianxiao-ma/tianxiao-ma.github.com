---
title: Hello Jekyll
layout: post
permalink: /2013/01/hello-jekyll/
date: Sat Jan 19 01:03:00 am GMT+8 2013
published: true
---
**目录**
===============
1. [Jelly介绍](#Jekyll)

   1.1. [什么是Jekyll](#jekyll-what)

   1.2. [Jekyll包含的工具集](#jekyll-toolkit)

   1.3. [使用Jekyll撰写blog](#use-jekyll-to-blog)

   1.4. [其他特性](#more-jekyll)

2. [搭建个人博客系统](#build-your-blog)

最近一时兴起，注册了独立域名，准备将平时记录的一些文档放到域名下面来，做一个个人的Blog。于是开始上网找，看看用那个工具搭建个人博客比较方便，最后用了[github][]的[pages][]，把[我的blog](http://tianxiao.in)托管到[github][]上，下面就来说说这个过程。

[github]: https://github.com/
[pages]: http://pages.github.com/

1. <span id="Jekyll">Jekyll</span>
==============
首先来说说[jekyll][]，因为这是[github pages][]采用的博客发布框架，而了解这个框架是整个过程中最重要的部分，所以就放到第一个来说。

1.1. <span id="jekyll-what">什么是Jekyll</span>
--------------

从表面上来看[jekyll][]是一个纯文本到静态html页面的转换框架，深入一点来看[jekyll][]本身是一个*'胶水'*，它合理地选择了一组功能强大并且简单易用的工具，然后将这些工具*'粘贴'*到一起，形成一个适合用纯文本来编写和发布静态html页面的框架。而[github]则将这个框架以服务的形式免费向公众开放。

1.2. <span id="jekyll-toolkit">Jekyll包含的工具集</span>
-------------

[jekyll]主要包含了以下几种工具，

+   [YMAL][]
	这是一个人类可读的、跨语言的、基于Unicode的数据序列化语言，它的设计中包含了很多常用编程中的数据类型定义。它可以被用来编写配置文件或者用来序列化编程语言中的数据结构。由于这是一个规范，因此通过为不同语言编写解析器，就可以达到跨语言或者跨网络共享数据的目的。目前基本上所有流行的编程语言都有各自语言实现的[YMAL][]解析器。
	在[jekyll]中，它被用来为每个页面提供配置信息或者元数据。
+   [Liquid][]
	这是一个模版语言，在[jekyll][]中的作用类似于[Velocity][]和[FreeMarker][]，只不过它是为[Ruby on Rails][]应用开发的。
+   [MarkDown][]
	这是一个文本到html页面的转换工具，它定义了一些可读性强并且简单易用的文本标记，通过在纯文本中使用这些标记，经过[MarkDown][]翻译器翻译之后，就会变成html页面。本文就是使用[MarkDown]编写的，这是我们使用[jekyll][]作为个人博客发布管理框架之后，最常使用到的一个工具。
+   [Textile][]
	功能于[MarkDown][]相同，[jekyll][]同时支持[MarkDown]和[Textile]作为内容格式。

1.3. <span id="use-jekyll-to-blog">使用Jekyll撰写blog</span>
------------------

为了使用[jekyll]这个框架，首先需要创建一个符合*jekyll*要求的目录结构，下面是一个例子。关于各个目录用途在[jekyll]的站点上已经有了详细的介绍，点击[这里][jekyll usage]可以查阅。这里对*_posts*目录展开介绍一下。

![Jekyll directory layout example](/images/2013-01/hello-jekyll/jekyll-dir-layout-example.png)

我们通过[MarkDown][]或者[Textile][]编写的blog，都需要存在到这个目录下面，文件必须以*年-月-日-blog名字*格式命名，这样便于[jekyll][]对这些日志进行排序。当然我们也可以通过其他方式来指定日志的产生日期，让[jekyll][]按照我们指定的日期对日志进行排序，这个将在后面进行介绍。

每一篇blog会包含两个部分，一个是头部信息区或者元数据区，另外一个是正文区。[jekyll][]使用[YMAL][]作为定义元数据区的语言，关于元数据区的介绍参见[这里][jekyll ymal front matter]。值得一提的是，我们可以通过在元数据区定义一个*date*参数，来覆盖日志文件名中的时间信息，从而让[jekyll][]按照我们指定的时间对日志进行排序。由于[jekyll][]中使用[Ruby][]版本的*YMAL*解析器，所以*date*参数的值需要符合*Ruby*的时间函数要求，以GMT时间为例，我们可以这样来定义时间：`Sat Jan 19 01:03:00 am GMT+8 2013`，表示的是北京时间2013年1月19号凌晨1点03分。当然，*Ruby*也支持其他类型的时间日期格式，大家可以采用自己习惯的格式来定义这个时间。

正文是使用[MarkDown][]或者[Textile][]写成的，这篇文章使用了*MarkDown*。从学习和使用的体验来说，*MarkDown*是一个非常易学易用的工具，可以说基本不用学，看一些例子就会写了，这也是当初*MarkDown*的设计宗旨之一(题外话，*MarkDown*的设计者之一Aaron Swartz已经于2013年1月11日自杀身亡了)。

另外你还可以为你的blog站点设计一个美观的页面布局，页面布局用到的html文件需要放在*_layout*目录下面，通过[Liquid][]，[jekyll][]会将生成好的日志文章内容嵌入到这些布局中去。

1.4. <span id="more-jekyll">其他特性</span>
-------------------------------------------

作为一个博客站点，除了能够展示博客内容之外，博客列表分页、博客分类、博客永久连接、代码高亮、博客标签这些功能也都是必不可少的，[jekyll][]对于这些功能都有非常好的支持。除此之外，[jekyll][]还提供了将其他站点的博客系统[迁移到jekyll][blog migrations]的功能。这些功能的使用方式可以参考[github][]上使用*jekyll*的[其他人的博客系统](http://wiki.github.com/mojombo/jekyll/sites)。

2. <span id="build-your-blog">搭建个人博客站点</span>
====================================================

下面来说说如何搭建自己的博客系统，步骤如下：

1. 申请独立域名。有很多域名管理网站提供这样的服务，上[百度](http://www.baidu.com)或者[Google](http://www.google.com)搜一下就可以了；
2. 注册[github][]账号，然后生成[github pages][pages]；
3. 将生成好的[github pages][pages]工程下载到本地，在工程中添加一个*CNAME*文件，将你之前申请的域名写入这个文件中，然后将修改上传到[github][]服务器；
4. 在你申请域名的网站上为你的域名添加一条A记录，指向ip地址：`204.232.175.78`。配置的生效时间取决于各个管理网站，可能是1个小时，也可能需要1天；

完成了上面的配置工作之后，就可以用域名访问你在[github][]上的[github pages][]页面了。*github*为你生成的*pages*是一个标准的[jekyll][]目录结构，因为[github pages][pages]本省就是用[jekyll][]实现的。现在你就可以利用在这边文章中了解到的信息开始撰写你的日志了。

参考文献：
==========
[jekyll相关文档](http://jekyllrb.com/)

[blog migrations]: http://wiki.github.com/mojombo/jekyll/blog-migrations
[jekyll ymal front matter]: https://github.com/mojombo/jekyll/wiki/YAML-Front-Matter
[jekyll usage]: https://github.com/mojombo/jekyll/wiki/Usage
[jekyll]: https://github.com/mojombo/jekyll
[github pages]: http://pages.github.com/
[YMAL]: http://yaml.org/
[Velocity]: http://velocity.apache.org/
[FreeMarker]: http://freemarker.sourceforge.net/
[Liquid]: http://liquidmarkup.org/
[Ruby on Rails]: http://rubyonrails.org/ 
[MarkDown]: http://daringfireball.net/projects/markdown/
[Textile]: http://textile.thresholdstate.com/
[Ruby]: http://www.ruby-lang.org/en/
