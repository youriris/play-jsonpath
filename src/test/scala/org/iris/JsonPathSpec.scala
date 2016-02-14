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
            store.book(?(%.price > 8.95)).title.as[String] mustBe "Sword of Honour"
            store.book(?(%.ratings.klass == "PR")).title.as[String] mustBe "Moby Dick"
            store.book(?(%.published > new DateTime(2016, 1, 1, 0, 0))).title.as[String] mustBe "Moby Dick"
        }
        "work for a filter on js object" in {
            store.bicycle(?(%.color == "blue")).price.as[Double] mustBe 21.95
        }
        "allow extraction" in {
            store.book(?(%.price > 100)).title match {
                case _: JpUndefined =>
                case JpDefined(value) => fail
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
            js.$.store.book(?(%.ratings)).title.as[String] mustBe "Sayings of the Century"
        }
    }
}