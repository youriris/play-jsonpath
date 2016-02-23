package org.jiris

import org.junit.runner.RunWith
import org.scalatest.junit.JUnitRunner
import org.scalatestplus.play.PlaySpec
import org.jiris.JsonPath.DynaJson
import play.api.libs.json._

@RunWith(classOf[JUnitRunner])
class PathMacroSpec extends PlaySpec {
    val js = Json.parse("""
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
    """)

    "Just" should {
        implicit val pathNaming = JsonPath.DefaultPathNaming
        val jsRoot = new DynaJson(js)
        "testing" in {
            import scala.reflect.runtime.{universe => ru}
            import scala.reflect.runtime.universe._
            import play.api.libs.json.Json._

            import JsonPath._
            val expr = ru.reify{js.$.store.book(0).title}
            val expr3 = ru.reify{js.$.store.book(?(%.price > 8.95)).title}
            val expr1 = ru.reify{
                val store = js.$.store
                store.book(?(%.price > 8.95)).title
            }
            val expr2 = ru.reify{
                println("10")
                val hgs = 20
                js.$.store
            }
            
            val mirror = ru.runtimeMirror(this.getClass.getClassLoader)
            val jlr = mirror.staticModule("play.api.libs.json.JsLookupResult")
            val jv = mirror.staticModule("play.api.libs.json.JsValue")
            val jp = mirror.staticModule("org.jiris.JsonPath.DynaJson")
            val jp2 = mirror.staticModule("org.jiris.JsonPath")
            val jsLookupResultToJsLookupFunc = Select(Ident(jlr), TermName("jsLookupResultToJsLookup"))
            val jsValueToJsLookupFunc = Select(Ident(jv), TermName("jsValueToJsLookup"))
            val applyFunc = Select(Ident(jp), TermName("apply"))
            
            object xformer extends Transformer {
                implicit class TreeWrapper(a: Tree) {
                    def \(y: List[Tree]) = {
                        Apply(applyFunc, List(transform(a), transform(y.head)))
                    }
                }
                
                implicit class SelectWrapper(s: Select) {
                    def \(y: List[Tree]) = {
                        Apply(applyFunc, List(s, transform(y.head)))
                    }
                }
                
                override def transform(tree: Tree): Tree = {
                    tree match {
                        case Apply(Select(Apply(Select(Apply(x2, y2), TermName("$")), y1), TermName("selectDynamic")), y) =>
                            Select(Apply(jsValueToJsLookupFunc, y2), TermName("result")) \ y // js.$.store
                        case Apply(Select(Select(Ident(m), TermName("$percent")), TermName("selectDynamic")), y)
                               if m.toTermName == TermName("JsonPath") =>
                            super.transform(tree)
                        case Apply(Select(a, TermName("selectDynamic")), y) =>
                            a \ y // book.title
                        case Apply(Select(Apply(Select(a1, TermName("applyDynamic")), y1), TermName("apply")), y) =>
                            a1 \ y1 \ y // store.book(0)
                        case _ => super.transform(tree)
                    }
                }
            }

            var newTree = xformer.transform(expr.tree)
            println("old Tree = " + expr.tree)
            println("old Tree raw = " + ru.showRaw(expr.tree))
            println("new Tree = " + newTree)
            newTree.toString() mustBe """DynaJson.apply(DynaJson.apply(DynaJson.apply(DynaJson.apply(JsValue.jsValueToJsLookup(PathMacroSpec.js).result, "store"), "book"), 0), "title")"""
            println("old Tree2 = " + expr2.tree)
            println("new Tree2 = " + xformer.transform(expr2.tree))
            println("old Tree3 = " + expr3.tree)
            println("new Tree3 = " + xformer.transform(expr3.tree))
            
            val r1 = ${js.$.store.book(0).title}
            println("title = " + r1.as[String])
            r1.as[String] mustBe "Sayings of the Century"
            val r2 = ${js.$.store.book(*(%.price > 8.95)).title}
            println("title = " + r2.as[List[String]])
            r2.as[List[String]] mustBe List("Sword of Honour", "Moby Dick", "The Lord of the Rings")
        }
    }
}