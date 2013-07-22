---
title: Java泛型要点总结-帮你用好Java泛型
layout: post
permalink: /2013/07/how-to-use-java-generlization/
date: Tue Jul 22 17:10:00 pm GMT+8 2013
published: true
---

使用[Java的泛型](http://docs.oracle.com/javase/tutorial/java/generics/)的好处总体来说有两个，一是可以提高代码的复用率，二是可以帮助程序员在编译期发现类型不匹配的错误。

但是在日常使用中，很多时候我们都会觉得Java的泛型用起来并没有那么爽，有时候都不知道为什么编译器会提示编译错误或者警告，翻来覆去的折腾，结果到最后还是无法避免使用强制类型转换。从个人使用Java泛型的经验来看，要用好Java泛型，一定要学会使用自限定泛型和桥接方法的使用。由于Java的泛型不是原生的，有点先天不足，自限定和桥接方法可以帮助我们弥补少许的不足。通过泛型的自限定，我们可以使得泛型类型可以协变，通过桥接方法，我们可以在必要的时候绕过编译期的类型检查。这两点是用好泛型必不可少的部分。

首先来看Java泛型的自限定。假设我们有一个`Person`类，定义如下：

{% highlight java linenos %}

	public interface Person extends Comparable<Person> {
		/**
		 * 返回姓名 
		 * @return
		 */
		public String getName();
		
		/**
		 * 返回年龄
		 * @return
		 */
		public String getGender();
	}

{% endhighlight %}

为了说明如何使用泛型的自限定，这里让`Person`类继承了`Comparable<T>`接口。有一个叫做*码农*的子类实现了`Person`接口，这个类的定义如下：

{% highlight java linenos %}

	public class Programmer implements Person {
	
		@Override
		public String getName() {
			return "Jack";
		}
	
		@Override
		public String getGender() {
			return "male";
		}
	
		@Override
		public int compareTo(Person o) {
			return 0;
		}
	}

{% endhighlight %}

可以看到，`Programmer`实现了`Person`定义的所有方法，包括从`Comparable`接口继承过来的`compareTo`方法。但是这里明显有一个问题，就是`Programmer`类中的`compareTo`方法的参数是`Person`类型，但是我们更希望他是`Programmer`类型的，这样我们就不用当心让一个码农和一个不是码农的人做比较了。那么要怎么样做才能达到这个目的呢？对了，就是用泛型的自限定。修改后的`Person`和`Programmer`类的定义如下：

{% highlight java linenos %}

	public interface Person<T extends Person<T>> extends Comparable<T> {
		/**
		 * 返回姓名 
		 * @return
		 */
		public String getName();
		
		/**
		 * 返回年龄
		 * @return
		 */
		public String getGender();
	}
	
	public class Programmer implements Person<Programmer> {
		@Override
		public String getName() {
			return "Jack";
		}
	
		@Override
		public String getGender() {
			return "male";
		}
	
		@Override
		public int compareTo(Programmer o) {
			return 0;
		}
	}

{% endhighlight %}

上面代码的第一行`Person`接口的定义包含了自限定的使用方法，`T extends Person<T>`。意思是给`Person`类一个类型参数`T`，这个类型是`Person<T>`或者`Person<T>`的子类，如果对Java泛型有一定理解的话，这个定义还是比较容易理解的，要是不理解也没关系，先记住就好了。抛开`Person`类，这样的定义对于`Person`的子类的影响就是如果继承`Person`类，那么类型参数必须是子类本身，从上面代码的`Programmer`类可以看出这一点。在`Programmer`类的定义中，如果把`Person`类的类型参数制定成`Person`类的其他子类，也是可以通过编译的，只不过我们通常都不会这么做，也没有什么太大意义。使用自限定的目的就是通过这种方式来模拟类型的协变，从`Programmer`类的`compareTo`方法可以看出这一点，参数已经变成`Programmer`类了，效果等同于参数类型随着继承也发生了相应的泛化。

再来看一个更复杂的例子，假设出现了变异人(X战警，你们懂的)，这些变异人除了拥有普通人的所有特征之外，还有一些特殊的能力，我们可以定义一个`XMen`类来表示这一点，代码如下：

{% highlight java linenos %}

	public class XMen<T extends Person<T>> implements Person<XMen<T>> {
		private T person;
	
		public T getPerson() {
			return person;
		}
		
		public void setPerson(T person) {
			this.person = person;
		}
		
		@Override
		public int compareTo(XMen<T> o) {
			return this.person.compareTo(o.person);
		}
	
		@Override
		public String getName() {
			return person.getName();
		}
	
		@Override
		public String getGender() {
			return person.getGender();
		}
	}

{% endhighlight %}

`XMen`类继承自`Person`类，所以跟`Programmer`类一样，给`Person`类的类型参数指定了`Xmen`类自己。此外，`Xmen`类自己也是一个参数类型，类型参数是`T extends Person<T>`，`XMen`类中的私有变量`person`就是这个类型的。基于`Person`通过其泛型定义给子类添加的约束，`Person`类的类型参数必须是子类自身。综合下来就有了上面的定义，给`Person`类的类型参数指定的是`XMen<X>`。

> Tips：Java的泛型语法规定，如果继承一个参数类型，那么在定义子类的时候必须为被继承的类的类型参数指定具体类型。也就是说，如果`A`继承了一个参数类型`B<T>`， 那么在定义`A`时，必须为`B`的类型参数指定一个具体的类型。如果`A`本身也是参数类型，那么`A`可以把自己的类型参数指定作为`B`的类型参数的类型。同时，在为`B`的类型参数指定类型时，不能使用`wild card`，也不能使用`extends`、`super`等关键字。也就是说类似于`class A extends B<? extends C>`或者`class A super B<? extends C>`这样的定义是无法通过编译的。

使用自限定类型的最大好处是可以让类型参数随着泛化而发生协变，就像前面代码给出的`Programmer`和`XMen`类一样，这两个类的`compareTo`方法的参数都是类自己，这种效果在某些编程场景下是非常有用的，如果没有协变是无法做到。这种方式不进行可以用到方法的参数上，也可以用在返回值上，比如下面的例子：

{% highlight java linenos %}

	public interface Person<T extends Person<T>> extends Comparable<T> {
		/**
		 * 返回姓名 
		 * @return
		 */
		public String getName();
		
		/**
		 * 返回年龄
		 * @return
		 */
		public String getGender();
		
		public T getPerson();
	}
	
	public class Programmer implements Person<Programmer> {
		@Override
		public String getName() {
			return "Jack";
		}
	
		@Override
		public String getGender() {
			return "male";
		}
	
		@Override
		public int compareTo(Programmer o) {
			return 0;
		}
	
		@Override
		public Programmer getPerson() {
			return this;
		}
	}

{% endhighlight %}

上面这段代码中，`Person`中定义了一个`getPerson()`方法，返回类型就是`Person`类的类型参数，`Programmer`类继承`Person`之后，其中的`getPerson()`方法的返回值也就相应地变成了`Programmer`类。这是让返回值发生协变的一种方式，除了这个方式之外，Java也支持通过继承让返回值发生协变，大概是下面这样的：

{% highlight java linenos %}

	class ClassA {}
	
	class ClassB extends ClassA {}
	
	class Dad {
		ClassA getInterfaceA() {
			return null;
		}
	}
	
	class Son extends Dad {
		@Override
		ClassB getInterfaceA () {
			return null;
		}
	}

{% endhighlight %}

自限定的技巧除了用来定义泛型类只外还可以用来定义泛型方法，用自限定来定义泛型方法的代码如下，假设有一个书店，用户可以查看在这边书店中买过的所有书籍。

{% highlight java linenos %}

	public class BookStore {
		public <T extends Person<T>> PurchasuedBookRecorder<T> getRecoder() {
			return new PurchasuedBookRecorder<T>();
		}
		
		public static void main(String... args) {
			BookStore bookStore = new BookStore();
			
			PurchasuedBookRecorder<Programmer> recorder = bookStore.getRecoder();
			
			System.out.println(recorder);
		}
		
		public class PurchasuedBookRecorder<T extends Person<T>> {
			public List<PurchasedBook> getPurchaseBook(T person) { 
				return null;
			}
		}
	}

{% endhighlight %}

下面来看看使用自限定泛型声明的一些限制和不便之处。第一个限制是属性的声明，以上面的`Person`类为例，在不适用自限定类型参数的时候，像下面这样使用是完全没有问题的：

{% highlight java linenos %}

	public interface Person extends Comparable<Person> {
		/**
		 * 返回姓名 
		 * @return
		 */
		public String getName();
		
		/**
		 * 返回年龄
		 * @return
		 */
		public String getGender();
		
		public T getPerson();
	}

	public class Example {
		private Person person;
	}

{% endhighlight %}

当我们为`Person`类加上类型参数，并将类型参数以自限定的方式声明的时候，编译器就会警告说上面代码中`Example`类中对`person`属性的声明是一个*raw type*，让人看着相当不舒服。为了去掉这个编译器警告，我们不得不为`person`属性添加一个类型，由于`Person`类处在类层次结构的顶层，通常来说我们会以下面的方式进行声明：

{% highlight java linenos %}

	private Person<?> person;

{% endhighlight %}

通过使用通配符***?***问好通配符使得`person`属性可以引用`Person`类的任何子类。这种情况任何具有类型参数的泛型类在声明时都会碰到，当我们想使用同一个泛型超类引用不同子类时，我们不得不使用通配符来标识类型参数，虽然管用，但是看上去却非常古怪，让人不是很爽。

声明时碰到的问题还算好，下面的集中情况就更加让人抓狂了，

{% highlight java linenos %}

	public class PurchasuedBookRecorder<T extends Person<T>> {
		public List<PurchasedBook> getPurchaseBook(T person) { 
			return null;
		}
	}

{% endhighlight %}

上面这段代码定义了一个`PurchasuedBookRecorder`类，这个类有一个类型参数，采用自限定方式声明，我们可以像下面这样使用这个类：

{% highlight java linenos %}

	public class BookStore {
		public <T extends Person<?>> PurchasuedBookRecorder<T> getRecoder() {
			return new PurchasuedBookRecorder<T>();
		}
		
		public static void main(String... args) {
			BookStore bookStore = new BookStore();
			PurchasuedBookRecorder<Programmer> recorder = bookStore.getRecoder();
		}
	}
{% endhighlight %}

由于`Programmer`继承自`Person`类，因此满足`PurchasuedBookRecorder`对于类型参数的自限定要求，但是各种人，各种数不是都在公诉我们要用接口变成吗，那么把上面的代码改成下面的形式可以不是更好吗？

{% highlight java linenos %}

	public class BookStore {
		public <T extends Person<?>> PurchasuedBookRecorder<T> getRecoder() {
			return new PurchasuedBookRecorder<T>();
		}
		
		public static void main(String... args) {
			BookStore bookStore = new BookStore();
			PurchasuedBookRecorder<Person> recorder = bookStore.getRecoder();
		}
	}
{% endhighlight %}

对不起，这样是没有办法通过编译的，编译器会抱怨`Person`类不符合`PurchasuedBookRecorder`类对于类型参数的要求。同样的，使用`Person<?>`来代替`Person`也无法通过编译。这个限制告诉我们：
> 为了保证通用性，像`PurchasuedBookRecorder`这样用自限定方式来声明其他类作为类型参数的方式是不可取的，而是应该像`Person`这样，用来限制自己。

那么如何解决上面的问题呢？只要像下面这样修改一下`PurchasuedBookRecorder`类的定义就可以了：

{% highlight java linenos %}

	public class PurchasuedBookRecorder<T extends Person<?>> {
		public List<PurchasedBook> getPurchaseBook(T person) { 
			return null;
		}
	}

{% endhighlight %}

上面的这种声明方式的灵活性要好的多，像下面这样的声明方式都可以通过编译器的检查：

{% highlight java linenos %}

	PurchasuedBookRecorder<Person<?>> recorder = bookStore.getRecoder(); //ok, no warning
	PurchasuedBookRecorder<Programmer> recorder = bookStore.getRecoder(); //ok, no warning
	PurchasuedBookRecorder<Person> recorder = bookStore.getRecoder(); //ok, with warning
	
{% endhighlight %}

上面代码第三行由于使用了*rawtype*，所以会接到编译器的警告。

> Tips：我们如何理解上面出现的编译错误呢？通过一个简单的方法可以找出问题的根源，只要将给出的类型参数带入到泛型类或者泛型方法的类型参数声明中就可以找出问题来。以泛型方法`public <T extends Person<T>> PurchasuedBookRecorder<T> getRecoder()`为例，当我们使用`PurchasuedBookRecorder<Person<?>> recorder`这种方式接受泛型方法的赋值时，将`Person<?>`带入到`T extends Person<T>`中，替换掉类型参数`T`得到的结果是`Person<?> extends Person<Person<?>>`，很显然这样定义是不存在的。为了对比，再来看下`PurchasuedBookRecorder<Programmer> recorder`的情况，替换之后得到`Programmer extends Person<Programmer>`，这正式我们声明`Programmer`类时候采用的形式，而且也符合自限定给出限制。看上去Java编译器的检查非常的简单，仅仅做了真是类型替换参数类型，然后看结果是否符合类型参数的定义。这样做也是没有办法的事情，因为Java的泛型不是真正的泛型，是由编译器+语法伪装出来的一种泛型，相信以后的版本也不会有太大的变化。

泛型方法的声明和使用也会碰到类似的问题，让我来修改一下`BookStore`类的`getRecoder`方法的定义就可以说明这个问题，新的定义如下：

{% highlight java linenos %}

	public <T extends Person<T>> PurchasuedBookRecorder<T> getRecoder() {
		return new PurchasuedBookRecorder<T>();
	}
	
{% endhighlight %}

发现区别了吗？我们将泛型方法的类型参数改成了自限定形式，我们会碰到同样的问题，

{% highlight java linenos %}

	PurchasuedBookRecorder<Person<?>> recorder = bookStore.getRecoder(); // compile error!
	PurchasuedBookRecorder<Programmer> recorder = bookStore.getRecoder(); // ok

{% endhighlight %}

通过这个例子又可以总结出一条规律：
> 为了保证通用性，泛型方法的类型参数尽量不要使用自限定方式定义。

除了上面说的情况之外，有时候为了让泛型代码能够正常编译，还需要一些欺骗编译器的手段来辅助。这种手段就是桥接方法，在有些情况下只有使用这种方式，才能即保证代码的通过用性，有能够通过编译器的检查。来看下面的例子：

{% highlight java linenos %}

	interface FruitProcessor<T extends Fruit> {
		void process();
	}
	
	
	class AppleProcessor implements FruitProcessor<Apple> {
		@Override
		public void process() {
		}
		
	}
	class OrangeProcessor implements FruitProcessor<Orange> {
		@Override
		public void process() {
			
		}
		
	}
	
	class Fruit{}
	class Apple extends Fruit{}
	class Orange extends Fruit{}
	
	class Main {
		Map<String, FruitProcessor<Fruit>> processorMap;
		
		Main() {
			processorMap = new HashMap<String, FruitProcessor<Fruit>>();
			processorMap.put("apple", new AppleProcessor()); // compile error
			processorMap.put("apple", (FruitProcessor<Fruit>)new AppleProcessor()); // compile error
			processorMap.put("orange", new OrangeProcessor()); // compile error
		}
	}

{% endhighlight %}

上面的代码定义了几个处理水果的类，在代码最后的`Main`类中像将多个不同的`FruitProcessor`类放到一个`Map`中，然后通过不同的`key`来调用不同的水果处理类。因为`FruitProcessor`是`AppleProcessor`和`OrangeProcessor`的超类，所以这样的做法是合情合理的，但是上面的代码却没有办法通过编译，所有尝试设置的操作都没有能够逃过编译器的眼睛。为了达到我们的目的，我们需要通过桥接方法来骗过编译器，下面是代码，仅给出了`Main`的代码。

{% highlight java linenos %}

	class Main {
		Map<String, FruitProcessor<Fruit>> processorMap;
		
		Main() {
			processorMap = new HashMap<String, FruitProcessor<Fruit>>();
			processorMap.put("apple", new AppleProcessor()); // compile error
			processorMap.put("apple", (FruitProcessor<Fruit>)new AppleProcessor()); // compile error
			processorMap.put("orange", new OrangeProcessor()); // compile error
			
			processorMap.put("apple", newAppProcessor()); // nick
			processorMap.put("orange", newOrangeProcessor()); // nick
		}
		
		private <T extends Fruit> FruitProcessor<T> newAppProcessor() {
			AppleProcessor appleProcessor = new AppleProcessor();
			return (FruitProcessor<T>) appleProcessor; // unchecked case warning
		}
		
		private <T extends Fruit> FruitProcessor<T> newOrangeProcessor() {
			OrangeProcessor orangeProcessor = new OrangeProcessor();
			return (FruitProcessor<T>) orangeProcessor; // unchecked case warning
		}
	}

{% endhighlight %}

上面的代码中定义了两个泛型桥接方法`newAppProcessor`和`newOrangeProcessor`，在这两个方法中，都使用了强制类型转换来将子类强行转型到父类，同时还将子类的类型参数也向上进行了转型，使得返回结果能够被放到`processorMap`中。为了的到通用性，我们付出了多写两个方法以及多了*unchecked case warning*警告，其实是相当坑爹的。在程序的运行过程中，所有参数类型的类型参数都会被擦除，Java虚拟机看到类型参数一律都是`Object`对象，我们在这里这么做完全是为了欺骗编译器。

总的来说，Java泛型虽然经常坑爹，有时候甚至让人觉得很奇怪，但是给程序员带来的帮助还是大于烦恼的，但是该鄙视的时候还是要毫不犹豫的加以鄙视。。。。