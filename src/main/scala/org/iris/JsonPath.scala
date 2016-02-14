package org.iris

import scala.language.dynamics

import play.api.libs.json.Json._
import play.api.libs.json._
import org.joda.time.DateTime
import org.joda.time.DateTimeZone
import scala.reflect.runtime.universe._

object JsonPath {
  	sealed trait PathNaming {
  	    def toJsonKey(key: String): String
  	}

  	object DefaultPathNaming extends PathNaming {
  	    def toJsonKey(key: String) = key
  	}
  	
  	object DynaJson {
  	    def apply(jsValue: JsValue)(implicit ns: PathNaming) = new DynaJson(jsValue)
  	    def apply(jsValue: Option[JsValue])(implicit ns: PathNaming) = new DynaJson(jsValue)
  	    def apply(lookupResult: JsLookupResult)(implicit ns: PathNaming) = new DynaJson(lookupResult)
  	}
  	
  	object JpDefined {
  	    def unapply(doc: DynaJson): Option[JsValue] = {
  	        doc.lookupResult match {
  	            case JsDefined(js) => Some(js)
  	            case _ => None
  	        }
  	    }
  	}
  	
  	trait JpUndefined
  	
  	class DynaJson(val lookupResult: JsLookupResult)(implicit ns: PathNaming) extends Dynamic {
  	    def this(jsValue: JsValue)(implicit ns: PathNaming) = this(new JsDefined(jsValue))
  	    def this(jsValue: Option[JsValue])(implicit ns: PathNaming) = this(jsValue match {
  	        case Some(v) => new JsDefined(v)
  	        case None => new JsUndefined("")
  	    })
  	    
  	    def selectDynamic(key: String) = apply(key)
  	    
  	    def applyDynamic(key: String) = new DynaJsonApplyDynamic(this, key)
  	    
  	    def apply(key: String) = {
  	        lookupResult \ ns.toJsonKey(key) match {
  	            case r: JsUndefined => new UndefinedDynaJson(r)
  	            case r => new DynaJson(r)
  	        }
  	    }
  	    
  	    def apply(filter: PathFilter) = filter.find(this)
  	
  	    def apply(index: Int) = 
  	        lookupResult(index) match {
  	            case r: JsUndefined => new UndefinedDynaJson(r)
  	            case r => new DynaJson(r)
  	        }
  	    
  	    def getOrElse(e: => DynaJson) = this match {
  	        case _: JpUndefined => e
  	        case _ => this
  	    }
  	    
  	    def jsValue = lookupResult.get
    }
  	
  	protected class UndefinedDynaJson(lookupResult: JsLookupResult)(implicit ns: PathNaming) extends DynaJson(lookupResult) with JpUndefined {
  	    override def jsValue = null
  	}

  	protected class DynaJsonApplyDynamic(path: DynaJson, key: String) {
  	    def apply(index: Int) = path.apply(key).apply(index)
  	    
  	    def apply(filter: PathFilter) = path.apply(key).apply(filter)
  	}
  	
  	protected abstract class JpExpression[R] {
  	    def <(r: JpExpression[_]) = new JpBinaryExpression(this, r) {
            override def _evaluate[T: Ordering](l: T, r: T) = compare(l, r) < 0
        }
  	    
  	    def <=(r: JpExpression[_]) = new JpBinaryExpression(this, r) {
            override def _evaluate[T: Ordering](l: T, r: T) = compare(l, r) <= 0
        }
  	    
  	    def >(r: JpExpression[_]) = new JpBinaryExpression(this, r) {
            override def _evaluate[T: Ordering](l: T, r: T) = compare(l, r) > 0
        }
  	    
  	    def >=(r: JpExpression[_]) = new JpBinaryExpression(this, r) {
            override def _evaluate[T: Ordering](l: T, r: T) = compare(l, r) >= 0
        }
  	    
  	    def ==(r: JpExpression[_]) = new JpBinaryExpression(this, r) {
            override def _evaluate[T: Ordering](l: T, r: T) = compare(l, r) == 0
        }
  	    
  	    def evaluate(filter: PathFilter, path: DynaJson)(implicit PathNaming: PathNaming): R
  	}
  	
  	protected abstract class JpBinaryExpression(l: JpExpression[_], r: JpExpression[_]) extends JpExpression[DynaJson] {
  	    val mirror = runtimeMirror(getClass.getClassLoader)
  	    
  	    def evaluate(filter: PathFilter, path: DynaJson)(implicit PathNaming: PathNaming) = {
  	        val toStringOrDouble = (v: Any) => v match {
  	            case i: Int => i.toDouble
  	            case i: java.lang.Integer => i.toDouble
  	            case d: java.lang.Double => d.toDouble
  	            case n: JsNumber => n.as[Double]
  	            case s: JsString => s.as[String]
  	            case other => other.toString
  	        }
  	        
  	        
  	        val normalize = (l: Any, r: Any) => {
  	            l match {
  	                case s: String => r match {
  	                    case r: DateTime => (new DateTime(s, DateTimeZone.UTC), r.getMillis)
  	                    case r: Double => (s, r.toString)
  	                    case _ => (s, r)
  	                }
  	                case d: Double => r match {
  	                    case r: String => (d.toString, r)
                        case _ => (d, r)
  	                }
  	            }
  	        }
  	        
  	        val result = l match {
  	            case selection: JpPathExpression => 
  	                selection.evaluate(filter, path).asInstanceOf[Map[Any, JsObject]].filter{ c =>
              	        val (lvalue, rvalue) = normalize(toStringOrDouble(c._1), toStringOrDouble(r.evaluate(filter, path)))
              	        
              	        rvalue match {
              	            case rvalue: Double => 
                  	            _evaluate[Double](lvalue.asInstanceOf[Double], rvalue.asInstanceOf[Double])
                  	        case rvalue: String => 
                  	            _evaluate[String](lvalue.asInstanceOf[String], rvalue.asInstanceOf[String])
              	        }
  	                }.map{ c => c._2 }
  	        }
  	        new DynaJson(result.headOption match {
  	            case Some(r) => new JsDefined(r)
  	            case None => new JsUndefined("")
  	        })
  	    }
  	    
  	    def _evaluate[T: Ordering](l: T, r: T): Boolean
  	    
  	    implicit def compare[T: Ordering](l: T, r: T) = implicitly[Ordering[T]].compare(l, r)
  	}
  	
  	protected class JpConstantExpression(v: Any) extends JpExpression[Any] {
  	    def evaluate(filter: PathFilter, path: DynaJson)(implicit PathNaming: PathNaming) = v
  	}
  	
  	protected class JpPathExpression(paths: Either[String, Int]*) extends JpExpression[Map[JsValue, JsValue]] with Dynamic {
  	    def selectDynamic(path: String) = apply(path)
  	    
  	    def applyDynamic(path: String)(index: Int) = apply(path).apply(index)
  	    
  	    def apply(path: String) = {
  	        new JpPathExpression((paths.toSeq ++ Seq(Left(path))): _*)
  	    }
  	    
  	    def apply(index: Int) = new JpPathExpression((paths.toSeq ++ Seq(Right(index))): _*)
  	    
  	    override def evaluate(filter: PathFilter, doc: DynaJson)(implicit PathNaming: PathNaming) = {
  	        doc.lookupResult match {
  	            case JsDefined(jsValue) =>
  	                val values = jsValue match {
  	                    case a: JsArray => a.value
  	                    case m: JsObject => m.values
  	                    case v => Seq(v) 
  	                }
              	    values.map { o => 
      	                var node = new DynaJson(new JsDefined(o))
      	                paths.foreach{ path =>
      	                    node = path match {
      	                        case Left(s) => node.apply(s)
      	                        case Right(i) => node.apply(i)
      	                    }
      	                }
      	                node.lookupResult -> o
  	                }.filter { resultToJsObj =>
          	            resultToJsObj._1.isInstanceOf[JsDefined]
          	        }.map { resultToJsObj =>
          	            resultToJsObj._1.get -> resultToJsObj._2
          	        }.toMap
  	            case _: JsUndefined => Map.empty
  	        }
  	    }
  	}
  	
  	trait PathFilter {
  	    def find(path: DynaJson)(implicit PathNaming: PathNaming): DynaJson
  	}
  	
  	protected class OptionFilter(e: JpExpression[_]) extends PathFilter {
  	    def find(path: DynaJson)(implicit PathNaming: PathNaming): DynaJson = {
  	        val r = e.evaluate(this, path) 
  	        r match {
  	            case doc: DynaJson => doc
  	            case m: Map[_, _] => 
  	                val v = m.headOption match {
      	                case Some(e) => JsDefined(e._2.asInstanceOf[JsValue])
      	                case None => new JsUndefined("")
      	            }
  	                new DynaJson(v)
  	        }
  	    }
  	}
  	
  	protected class ListFilter(e: JpExpression[_]) extends PathFilter {
  	    def find(path: DynaJson)(implicit PathNaming: PathNaming): DynaJson = {
  	        e.evaluate(this, path).asInstanceOf[DynaJson]
  	    }
  	}

  	implicit def DynaJsonToJsLookupResult(doc: DynaJson) = doc.lookupResult

    implicit def intToExpression(i: Int) = new JpConstantExpression(i)

    implicit def doubleToExpression(d: Double) = new JpConstantExpression(d)

    implicit def stringToExpression(s: String) = new JpConstantExpression(s)

    implicit def dateTimeToExpression(dt: DateTime) = new JpConstantExpression(dt)

  	implicit class JsValueWrapper(jsValue: JsValue) {
        def $(implicit PathNaming: PathNaming) = new DynaJson(jsValue)
    }
    
  	def ?(e: JpExpression[_]) = new OptionFilter(e)
  	
  	def *(e: JpExpression[_]) = new ListFilter(e)

  	def % = new Dynamic {
  	    def selectDynamic(key: String) = new JpPathExpression(Left(key))
  	    def applyDynamic(key: String) = new JpPathExpression(Left(key))
  	}
  	
    implicit val dateTimeReads = new Reads[DateTime] {
        def reads(js: JsValue) = {
            JsSuccess(new DateTime(js.as[String], DateTimeZone.UTC))
        }
    }
}