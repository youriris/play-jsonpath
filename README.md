# play-jsonpath

play-jsonpath is a jsonpath implementation that works with play's json library.

  - $: start a jsonpath expression on a JsonValue
  - *: select any children of the current node
  - ?(selector): selects one or no child
  - *(selector): selects all children that meets the selector
  - %: current node
  
### Usage
For the following json document:
```json
{ "store": {
    "book": [
      { "category": "reference",
        "author": "Nigel Rees",
        "title": "Sayings of the Century",
        "price": 8.95,
        "ratings": {
          "klass": "R"
        }
      },
      { "category": "fiction",
        "author": "Evelyn Waugh",
        "title": "Sword of Honour",
        "price": 12.99,
        "published": "2015-04-07T12:00:00.000Z"
      },
      { "category": "fiction",
        "author": "Herman Melville",
        "title": "Moby Dick",
        "isbn": "0-553-21311-3",
        "price": 8.99,
        "ratings": {
          "klass": "PR"
        },
        "published": "2016-04-07T12:00:00.000Z"                
      },
      { "category": "fiction",
        "author": "J. R. R. Tolkien",
        "title": "The Lord of the Rings",
        "isbn": "0-395-19395-8",
        "price": 22.99
      }
    ],
    "bicycle": {
      "XGZ193-R" : {
        "color": "red",
        "price": 19.95
      },
      "XGZ193-G" : null,
      "XGZ193-B" : {
        "color": "blue",
        "price": 21.95
      }
    }
  }
}
```
select third book's klass:
```scala
scala> store.book(2).ratings.klass.as[String]
"PR"
```
select all klasses:
```scala
scala> store.book(*).ratings.klass.as[List[String]]
List("R", "PR")
```
a wild-card for this index of an array can be omitted:
```scala
scala> store.book.ratings.klass.as[List[String]]
List("R", "PR")
```
find a book with the price higher than 8.95:
```scala
scala> store.book(?(%.price > 8.95)).title.as[String]
"Sword of Honour"
```
find a book with klass of PR:
```scala
scala> store.book(?(%.ratings.klass == "PR")).title.as[String]
"Moby Dick"
```
find a blue bicylce's price:
```scala
scala> store.bicycle(?(%.color == "blue")).price.as[Double]
21.95
```
find titles of all books of the price <= 20 and the fiction category:
```scala
scala> store.book(*(%.price <= 20 && %.category == "fiction")).title.as[List[String]]
List("Sword of Honour","Moby Dick")
```
find all books' klasses:
```scala
scala> store.book.*.klass.as[List[String]]
List("R", "PR")
```
### Samples
* [JsonPathSpec.scala] [spec]

### License
Apache 2

### Building

You need sbt installed.

```sh
$ git clone https://github.com/youriris/play-jsonpath.git
$ cd play-jsonpath
$ sbt eclipse
```

[spec]: <https://github.com/youriris/play-jsonpath/blob/master/src/test/scala/org/jiris/JsonPathSpec.scala>




