---
title: 理解maven assembly的工作方式
layout: post
permalink: /2013/09/maven-assembly
date: Tue Sep 10 23:50:00 pm GMT+8 2013
published: true
---

`maven`的`assembly`插件一般会被用来将多个不同位置的文件打包一起形成一个发布包，比如war包、ear包等等。`assemly`插件不仅可以单独在命令行执行，也可以配合`maven`的生命周期执行。命令行的使用方式基本是下面的这种形式:

{% highlight bash linenos %}

mvn clean package -U -Dmaven.test.skip=true assembly:[assembly|single]

{% endhighlight %}

在配合maven的生命周期执行时，则需要需要在`<plugin>`标签下的`<executions>`标签中进行定义，比如下面的这种方式：

{% highlight xml linenos %}

<project>
	...
	<build>
		<plugins>
			<plugin>
				<artifactId>maven-assembly-plugin</artifactId>
				<executions>
					<execution>
						<id>assemle1</id>
						<phase>package</phase>
						<configuration>
							...
						</configuration>
						<goals>
							<goal>single</goal>
						</goals>
					</execution>
				</executions>
			</plugin>
		</plugins>
	</build>
	...
</project>

{% endhighlight %}

目前`assembly`插件的`assembly`是使用最多的一个goal，因为这是assembly插件早期的版本的一个goal，但是在较新版本中这个goal已经被**`deprecated`**掉了，可以参考[这个插件的文档](https://maven.apache.org/plugins/maven-assembly-plugin/plugin-info.html)，摘要如下：

> assembly:assembly	Deprecated. Use assembly:single instead! The assembly:assembly mojo leads to non-standard builds.
 
我们知道maven的pom文件是具有继承关系的，在构建多模块工程的时候，子模块中的pom会继承父模块中pom的各种定义，当然也会继承plugin的定义，但是`assembly`插件的`assembly`这个goal并没有遵循这个规则，这个goal只会在package阶段结束之后执行一次。为了说明这个问题，让我们看一下下面这个maven工程的assemlby过程，工程的目录结构如下：

{% highlight bash linenos %}

mvn-app
	| module1
	|	| src
	|	| pom.xml
	|	| release.xml
	| module2
	|	| src
	|	| pom.xml
	| target
	| pom.xml
	| release.xml

{% endhighlight %}

`mvn-app`包含两个子模块，分别是`module1`和`module2`，其中三个pom文件的定义如下：

mvn-app工程总的pom文件：
{% highlight xml linenos %}

...
	<modules>
        <module>module1</module>
        <module>module2</module>
    </modules>
    
    <build>
        <plugins>
            <plugin>
                <artifactId>maven-assembly-plugin</artifactId>
                <configuration>
                    <finalName>mvn-app</finalName>
                    <descriptors>
                        <descriptor>release.xml</descriptor>
                    </descriptors>
                </configuration>
            </plugin>
        </plugins>
    </build>
...

{% endhighlight %}

module1的pom文件：
{% highlight xml linenos %}

...
    <name>module1</name>

    <build>
        <sourceDirectory>src/main/java</sourceDirectory>
        <testSourceDirectory>src/test/java</testSourceDirectory>

        <plugins>
            <plugin>
                <artifactId>maven-assembly-plugin</artifactId>
                <configuration>
                    <finalName>module1</finalName>
                    <descriptors>
                        <descriptor>release.xml</descriptor>
                    </descriptors>
                </configuration>
            </plugin>
        </plugins>
    </build>
...

{% endhighlight %}

module2的pom文件：
{% highlight xml linenos %}

...
    <name>module2</name>

    <build>
        <sourceDirectory>src/main/java</sourceDirectory>
        <testSourceDirectory>src/test/java</testSourceDirectory>
    </build>
...

{% endhighlight %}

我们在`mvn-app`和`module1`的pom文件中使用了`assembly`插件，我们希望`mvn-app`的assembly插件在执行之后会在`mvn-app/target`目录下面生成一个名为`mvn-app`的打包文件，`module1`的assmbly插件在执行之后会在`module1/target`目录下生成一个名为`module1`的打包文件。但是，当执行`mvn assembly:assembly`命令之后却发现只在`mvn-app/target`目录下生成了`mvn-app`打包文件。这个过程有两个问题可以说明`assembly`插件的`assembly`这个goal没有遵循标准的构建过程：

* 首先，虽然我们在`module1`的pom文件中使用了`assembly`插件，但是却没有按照我们的要求打出相应的包来；
* 其次，`module2`没有在pom文件中使用`assembly`插件，但是由于pom的继承关系，应该会从父pom中继承关于`assembly`插件的定义，并且也应该会执行一次assemlby操作；

maven在构建多模块工程的时候，是会在每个模块上执行一遍构建命令的，也就是说`mvn assembly:assembly`这个命令应该分别会在`mvn-app`、`module1`和`module2`这个三个模块上执行一遍，这样`module1`中关于`assembly`插件应该会生效，而`module2`会由于继承了父pom中关于`assembly`插件的定义而导致构建失败，但是这两个事件都没有发生。

如果我们保留`module1`的pom文件中关于`assembly`插件的定义，而删除`mvn-app`的pom文件中关于`assembly`插件的定义，当重新执行`mvn assembly:assembly`命令的时候会发现构建过程出错，主要的错误信息是说在构建`mvn-app`这个模块的时候缺少部署描述符。`module1`中的`assembly`插件定义依然没有生效。

也就是说，`assembly`插件的`assembly`这个goal只会使用顶层pom文件中的定义(更确切地说应该是执行mvn命名时所在的那个目录中pom文件中的定义)，而不会使用子模块中定义。就算我们在子模块中配置使用了`assembly`插件，在使用这个goal的时候也不会生效。

> `assembly`这个goal是一个fork goal，因此我们可以直接使用`mvn assembly:assembly`这样的命令来完成构建。fork goal的意思是说在执行goal的时候maven会模拟执行生命周期中的一些阶段，然后在来执行相关的goal，具体到`assembly`这个goal来说，在执行的时候`assembly`插件会fork包括package阶段在内的所有阶段，然后再执行`assembly`这个goal。更详细的信息可以参考这个[链接](http://dongchimi.unfix.net/resources/openBooks/MavenTheDefinitiveGuide/assemblies.html)

下面再来说一下`assembly`插件的`single`这个goal，因为这是目前maven推荐使用的goal，这个goal是完全符合标准构建过程的。使用前面给出工程目录结构和pom文件，当我们执行`mvn clean; mvn package assembly:single`命令之后(`single`不是fork goal，所以需要执行package阶段)，会发现构建失败了。检查错误信息，会发现是因为在构建`mvn-app`这个模块的时候，由于缺少我们在部署描述文件中指明的用来构建打包的文件和目录(因为执行了`mvn clean`命令，打包需要的文件被清理掉了)，所以构建过程失败了。这一点正好说明了`single`这个goal会在每个模块的构建过程中都会执行，符合标准的maven构建过程。

现在问题就出现了，在使用`single`这个goal时改如何配置才能达到`assembly`这个goal的效果呢？比较好的办法是通过定义个专门用来打包的子模块解决这个问题，比如我们可以在前面给出的工程目录结构中，再添加一个`release`目录专门用来进行打包。现在整个工程的目录结构就变成了下面这个样子：

{% highlight bash linenos %}

mvn-app
	| module1
	|	| src
	|	| pom.xml
	|	| release.xml
	| module2
	|	| src
	|	| pom.xml
	| `release`
	|	| `pom.xml`
	|	| `release.xml`
	| target
	| pom.xml

{% endhighlight %}

为了能够正确打包，需要让release子模块依赖与module1和module2两个子模块，这样当release子模块执行构建的时候，打包需要的所有文件就都已经存在了。另外，我们将原本存在与`mvn-app`目录下面的`release.xml`文件也移到了`release`目录下。

在使用`single`这goal的情况下，由于`assembly`插件是遵循标准构建过程的，也就说插件的定义会被继承，同时每个子模块在构建的过程中都会执行assemlby操作，而在实际的项目并不是每一个模块都需要执行assembly操作的，对于`mvn-app`这个工程来说，我们只需要在`module1`和`release`这两个子模块的构建过程中使用`assembly`插件就可以了。这个时候我们需要使用`ignoreMissingDescriptor`这个`assembly`插件的配置项。如果没有这个配置项，maven在构建`mvn-app`工程的时候会由于发现`mvn-app`模块和`module2`模块没有在pom文件中指定部署描述文件(就是那个release.xml文件)而终止构建过程。

在做了上面的这些修改之后，当我们执行`mvn clean; mvn package assembly:single`命令之后，就会按照我们的要求在`mvn-app/target`目录和`module1/target`目录下面生成打包好的文件。

`assembly`这个goal在工程只需要打一个包或者工程只有一个模块的情况下可以考虑使用，如果工程有多个子模块需要使用`assembly`插件，则使用`single`这个goal以本文描述的方式打包会更好一些。

> 如果一个工程想要打多个不同的包，比如一个web工程，可能会想要针对不同的情况打出多个部署包，可以通过声明多个部署描述文件的方式完成(也就是多个release.xml文件)。这种方法有一个限制，就是打出来的包名会拥有固定的格式`<finalname>-<asseblyId>`，其中`<finalname>`通过`assembly`插件的`<finalName>`配置项指定，而`<assemblyId>`则通过部署描述符中的`<id>`配置项指定。如果希望绕过这个限制，则只能使用文章开头说结合maven生命周期的方式，通过配置多个`<execution>`可以达到这个目的(详见这个[链接](http://stackoverflow.com/questions/1326527/maven-assembly-plugin-custom-jar-filenames))。由于`assembly`这个goal会fork构建周期中的一些阶段，所以，在配合maven生命周期执行打包的时候，只需要执行`mvn assembly:assembly`就可以了，如果使用`mvn package assembly:assembly`会导致重复执行两次打包的情况出现。不信？可以自己试试的。

文章中用到的maven工程可以在这里[下载](https://github.com/tianxiao-ma/maven-explorer)



