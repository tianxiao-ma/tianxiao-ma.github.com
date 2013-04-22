---
title: How GGLIB Process Java Bridge Method
layout: post
permalink: /2013/04/how-cglib-process-java-bridge-method/
date: Wed Apr 22 22:55:00 pm GMT+8 2013
published: true
---

## Java中的Bridge(桥接)方法
Java编译器在编译Java源代码时，有些情况下会自动地生成一些方法，这些方法在源代码中是看不到的，这类方法叫做*synthetic method*，也就是人造方法，桥接方法就是其中一中。顾名思义，桥接方法是用来在两点之间建立联系的方法，这两点就是方法调用点和被调用的目标方法。下面来看两个例子，这两个例子展示了Java中桥接方法的主要使用场景。

**场景一：**

	public class BridgeMethodTest {
		static abstract class AbstractFoo {
			public String getSomeStrings() {
				return "Hello world!";
			}
		}
		
		public static class ConcreteFoo extends AbstractFoo {
		}
	}

上面这段代码定义了两个类，编译之后，我们利用javap来看一下`AbstractFoo`和`ConcreteFoo`，执行`javap -c BridgeMethodTest$AbstractFoo`和`javap -c BridgeMethodTest$ConcreteFoo`之后的结果如下：

	abstract class org.easymock.tests.BridgeMethodTest$AbstractFoo extends java.lang.Object{
		org.easymock.tests.BridgeMethodTest$AbstractFoo();
			Code:
				0:	aload_0
				1:	invokespecial	#1; //Method java/lang/Object."<init>":()V
				4:	return
		public java.lang.String getSomeStrings();
			Code:
				0:	ldc	#2; //String Hello world!
				2:	areturn
		}
	
	public class org.easymock.tests.BridgeMethodTest$ConcreteFoo extends org.easymock.tests.BridgeMethodTest$AbstractFoo{
		public org.easymock.tests.BridgeMethodTest$ConcreteFoo();
			Code:
				0:	aload_0
				1:	invokespecial	#1; //Method org/easymock/tests/BridgeMethodTest$AbstractFoo."<init>":()V
				4:	return
		public java.lang.String getSomeStrings();
			Code:
				0:	aload_0
				1:	invokespecial	#2; //Method org/easymock/tests/BridgeMethodTest$AbstractFoo.getSomeStrings:()Ljava/lang/String;
				4:	areturn
		}
	
我们看到反编译AbstractFoo.class和ConcreteFoo.class之后，发现ConcreteFoo的字节码中除了默认的构造函数之外，还有一个`getSomeStrings()`方法，而且其可见性、返回值和参数都跟AbstractFoo中的`getSomeStrings()`方法一模一样，但是我们的源代码中却没有定义这个方法。在看这个方法的方法体，其中只干了一件事情，就是调用AbstractFoo的`getSomeStrings()`方法。在这里，ConcreteFoo中的`getSomeStrings()`就是一个桥接方法。由于ConcreteFoo继承自AbstractFoo，这样它就继承了AbstractFoo中的`getSomeStrings()`方法，Java正式通过桥接方法来实现方法继承的。Java的编译器会为超类中每一个没有被覆写的方法在子类中生成一个桥接方法，在桥接方法中调用超类中相同的签名的方法。

**场景二：**

Java中另外一类需要用到桥接方法的情况是在编译泛型类型的时候。比如下面的代码：

	public class BridgeMethodTest {
		interface I <T> {
			T getSomething();
		}
		
		class A implements I<Integer> {
			public Integer getSomething() {
				return null;
			}
		}
	}
	
通过*javap*查看类A的字节码会发现是下面这样的(省略了构造函数的字节码)：
	
		class org.easymock.tests.BridgeMethodTest$A extends java.lang.Object implements org.easymock.tests.BridgeMethodTest$I{
		....
		public java.lang.Integer getSomething();
			Code:
				0:	aconst_null
				1:	areturn
				
			public java.lang.Object getSomething();
				Code:
					0:	aload_0
					1:	invokevirtual	#3; //Method getSomething:()Ljava/lang/Integer;
					4:	areturn
		}
		
编译之后的字节码中会找到两个`getSomething()`方法，两者的差别只是在返回类型上，一个返回Object，而另外一个则返回Integer。我们在定义类A的时候只定义了返回Integer的`getSomething()`方法， 所以返回Object的`getSomething()`方法是由编译器自动生成的，其中的逻辑就是调用返回Integer的那个`getSomething()`方法。这种类型的桥接方法是Java编译器为了向后做二进制兼容而生成的。因为泛型并不是java原生的属性，为了让以前的代码能够使用新版本java(加入了泛型之后)编译出来的字节码，需要对字节码做向后兼容。否则，以前的很多的代码就要重新写过，非常的麻烦。

## CGLIB对桥接方法的处理
CGLIB的一个作用是为类生成代理，由于Java的动态代理只能处理接口，所以很多框架，比如Spring、Hibernate等都会使用CGLIB来为类生成代理，作为一中实现AOP的机制。

CBLIB在为一个类生成子类的时候，会将每一个桥接方法的调用类型从`invokespecial`改为`invokevirtual`。这样做的目的是不让桥接方法将调用传递给超类中相同的方法，因为超类中的这个方法可能会继续将调用传递给超类的超类，这样CGLIB就没有办法保证所有的方法调用都会经过生成出来的代理类了。

所以，各个框架在调用由CBLIB生成的代理类中的桥接方法时，为了保证AOP切面的逻辑能够被执行到，都需要进行特殊的处理。

*备注：*invokespecial是java字节码的一个指令，这里的作用是调用超类中被继承的方法。invokevirtual也是java字节码的一个指令，用来调用实例方法。详细的介绍可以看[JVM规范的第四章](http://docs.oracle.com/javase/specs/jvms/se5.0/html/ClassFile.doc.html)。

