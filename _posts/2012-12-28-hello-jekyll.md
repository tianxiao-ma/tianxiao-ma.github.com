---
title: Hello Jekyll
layout: post
permalink: /2013/01/hello-jekyll/
date: Sat Jan 19 01:03:00 am GMT+8
published: false
---
最近一时兴起，注册了独立域名，准备将平时记录的一些文档放到域名下面来，做一个个人的Blog。于是开始上网找，看看用那个工具搭建个人博客比较方便，最后用了[github][]的[pages][]，把[我的blog](http://tianxiao.in)托管到[github][]上，下面就来说说这个过程。

[github]: https://github.com/
[pages]: http://pages.github.com/

Jekyll
==============
首先来说说[jekyll][]，因为这是[github pages][]采用的博客发布框架，而了解这个框架是整个过程中最重要的部分，所以就放到第一个来说。

什么是Jekyll
--------------
从表面上来看[jekyll][]是一个纯文本到静态html页面的转换框架，深入一点来看[jekyll][]本身是一个*'胶水'*，它合理地选择了一组功能强大并且简单易用的工具，然后将这些工具*'粘贴'*到一起，形成一个适合用纯文本来编写和发布静态html页面的框架。而[github]则将这个框架以服务的形式免费向公众开放。

Jekyll包含的工具集
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

[jekyll]: https://github.com/mojombo/jekyll
[github pages]: http://pages.github.com/
[YMAL]: http://yaml.org/
[Velocity]: http://velocity.apache.org/
[FreeMarker]: http://freemarker.sourceforge.net/
[Liquid]: http://liquidmarkup.org/
[Ruby on Rails]: http://rubyonrails.org/ 
[MarkDown]: http://daringfireball.net/projects/markdown/
[Textile]: http://textile.thresholdstate.com/
