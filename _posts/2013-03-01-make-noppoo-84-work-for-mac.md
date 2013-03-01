---
title: Make Noppoo 84 keyboard work for MacOS
layout: post
permalink: /2013/03/make_noppoo_84_work_for_mac_os/
date: Fri Mar 01 15:15:00 pm GMT+8 2013
published: true
---

网上买了个Noppoo 84青轴键盘，准备配合Macbook来用的。84键的键盘，小巧、手感也不错，但是发现MacOS不兼容这个键盘。在网上搜了一下，发现github上有一个项目可以通过添加系统扩展来使得MacOS来支持noppoo 84键盘。

刚开始，代码下下来编译、安装之后，发现无法使用，就跟项目的拥有者报告了这个问题，经过来回折腾了一段时间，这个驱动终于能够正常使用了。感谢这个驱动的提供者。

noppoo 84键盘的MacOS驱动地址：[MacOS driver for Noppoo 84 keyboard](https://github.com/thefloweringash/iousbhiddriver-descriptor-override)

这个驱动的功能简单来说，就是修正MacOS对noppoo 84键盘信号的解读方式，将USB接口传过来的信号，进行一些编码和解码操作，使得MacOS能够理解这个键盘的按键信号。感兴趣的读者，可以自己找一些相关的资料，了解一下键盘与操作系统交互的相关内容。
