package org.jiris

import scala.language.dynamics
import org.junit.runner.RunWith
import org.scalatest.junit.JUnitRunner
import org.scalatestplus.play.PlaySpec
import org.joda.time.DateTime
import play.api.libs.json._
import org.jiris.PathMacro._
import org.jiris.JsonPath.DynaJson

@RunWith(classOf[JUnitRunner])
class PathMacro2Spec extends PlaySpec {
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
           val expr = ru.reify{js.$.store.book(?(%.price > 8.95)).title}
           
           val mirror = ru.runtimeMirror(this.getClass.getClassLoader)
           val jlr = mirror.staticModule("play.api.libs.json.JsLookupResult")
           val jv = mirror.staticModule("play.api.libs.json.JsValue")
           val jp = mirror.staticModule("org.jiris.JsonPath.DynaJson")
           val jsLookupResultToJsLookupFunc = Select(Ident(jlr), TermName("jsLookupResultToJsLookup"))
           val jsValueToJsLookupFunc = Select(Ident(jv), TermName("jsValueToJsLookup"))
           val applyFunc = Select(Ident(jp), TermName("apply"))
           
           var pathNamingVar: List[Tree] = null

           def \(a: Tree, y: List[Tree]) = {
               Apply(applyFunc, List(traverse(a), y.head))
           }
           
           def applyPathNaming(inner: Apply) =                                                                        
               Apply(inner, List(Ident(TermName("pn"))))
           
           def traverse(tree: Tree): Tree = {
               tree match {
                   case Apply(x, y) =>
                       x match {
                           case Select(a, b) =>
                               if(b.toString == "selectDynamic") {
                                   a match {
                                       case Apply(x1, y1) =>
                                           x1 match {
                                               case Select(a1, b1) =>
                                                   if(b1.toString == "$") {
                                                       a1 match {
                                                           case Apply(x2, y2) =>
                                                               x1 match {
                                                                   case Select(a2, b2) =>
                                                                       val variable = Select(Apply(jsValueToJsLookupFunc, y2), "result")
                                                                       pathNamingVar = y1
                                                                       Apply(applyFunc, List(variable, y.head))
                                                                   case rest =>
                                                                       \(a, y)
                                                               }
                                                           case rest =>
                                                               \(a, y)
                                                       }
                                                   } else {
                                                       \(a, y)
                                                   }
                                               case _ => \(a, y)
                                           }
                                       case _ => \(a, y)
                                   }
                               } else if(b.toString == "apply") {
                                   a match {
                                       case Apply(x1, y1) =>
                                           x1 match {
                                               case Select(a1, b1) =>
                                                   if(b1.toString == "applyDynamic") {
                                                       val variable = \(a1, y1)
                                                       Apply(applyFunc, List(traverse(variable), traverse(y.head)))
                                                   } else {
                                                       Apply(Select(traverse(a), b), y)
                                                   }
                                               case rest =>
                                                   Apply(Select(traverse(a), b), y)
                                           }
                                       case rest =>
                                           Apply(Select(traverse(a), b), y)
                                   }
                               } else if(a.toString == "JsonPath" && b.toString == "$qmark") {
                                   Apply(Select(traverse(a), b), y.map {traverse(_)})
                               } else {
                                   Apply(Select(traverse(a), b), y)
                               }
                           case Apply(x1, y1) =>
                               x1 match {
                                   case Select(a, b) =>
                                       a match {
                                           case Apply(x2, y2) =>
                                               x2 match {
                                                   case Select(a1, b1) =>
                                                       a1 match {
                                                           case Select(a2, b2) =>
                                                               if(a2.toString == "JsonPath" && b2.toString == "$percent") {
                                                                 
                                                               }
                                                           case _ =>
                                                       }
                                                   case _ =>
                                               }
                                           case _ =>
                                       }
                                   case _ =>
                               }
                               Apply(x, y)
                           case rest =>
                               Apply(x, y)
                       }
                   case rest =>
                       rest
               }
           }

           var newTree = traverse(expr.tree)
           println("old Tree = " + expr.tree)
           println("new Tree = " + newTree)
           
           val r = jsonpath[JsLookupResult]{js.$.store.book(?(%.price > 8.95)).title}
           println("title=", r.as[String])
        }
    }
}