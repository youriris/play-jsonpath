package org.iris

import scala.language.dynamics

import org.junit.runner.RunWith
import org.scalatest.junit.JUnitRunner
import org.scalatestplus.play.PlaySpec
import org.joda.time.DateTime
import play.api.libs.json._

import org.iris.JsonPath._

@RunWith(classOf[JUnitRunner])
class JsonPathSpec extends PlaySpec {
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
              "XGZ193-B" : {
                "color": "blue",
                "price": 21.95
              }
            }
          }
        }
    """)
    
    implicit val pathNaming = DefaultPathNaming
    val store = js.$.store
    
    "Binary expressions" should {
        "work for a filter on js array" in {
            store.book(?(%.price > 100)).title.asOpt[String].isDefined mustBe false
            store.book(?(%.price < 100)).title.asOpt[String].isDefined mustBe true
            store.book(?(%.price > 8.95)).title.as[String] mustBe "Sword of Honour"
            // sorry, no == override for scala
            store.book(?(%.ratings.klass -> "PR")).title.as[String] mustBe "Moby Dick"
            store.book(?(%.published > new DateTime(2016, 1, 1, 0, 0))).title.as[String] mustBe "Moby Dick"
        }
        "work for a filter on js object" in {
            // bicycle is not an array but a map
            store.bicycle(?(%.color -> "blue")).price.as[Double] mustBe 21.95
        }
        "allow extraction" in {
            store.book(?(%.price > 100)).title.lookupResult match {
                case _: JsUndefined =>
                case JsDefined(value) => fail
            }
            //shortcut
            store.book(?(%.price > 100)).title match {
                case _: JpUndefined =>
                case JpDefined(value) => fail // value is a JsValue
            }
        }
        "allow getOrElse" in {
            store.book(?(%.price > 100)).title.getOrElse {
                store.book(?(%.price > 20)).title
            }.as[String] mustBe "The Lord of the Rings"
        } 
    }

    "Presence expressions" should {
        "work" in {
            store.book(?(%.ratings)).title.as[String] mustBe "Sayings of the Century"
        }
    }

    "Multi-row expressions" should {
        "work" in {
            store.book.ratings.klass.as[List[String]] mustBe List("R", "PR")
            store.book(?(%.price >= 9)).title.as[String] mustBe "Sword of Honour"
            store.book(*(%.price >= 9)).title.as[List[String]] mustBe List("Sword of Honour", "The Lord of the Rings")
            store.book(*(%.price <= 100)).title.as[List[String]].size mustBe 4
            store.book(*(%.category <> "reference")).title.as[List[String]].size mustBe 3
            
            store.book(*(%.category -> "fiction" && %.price <= 20)).title.as[List[String]] mustBe List("Sword of Honour","Moby Dick")
            store.book(*(%.category -> "fiction" || %.category -> "reference")).title.as[Set[String]] mustBe Set("Moby Dick", "Sword of Honour", "The Lord of the Rings", "Sayings of the Century")
        }
    }
}