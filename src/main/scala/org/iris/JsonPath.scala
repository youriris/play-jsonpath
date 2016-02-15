package org.iris

import scala.language.dynamics

import play.api.libs.json.Json._
import play.api.libs.json._
import org.joda.time.DateTime
import org.joda.time.DateTimeZone
import scala.reflect.runtime.universe._

object JsonPath {
      trait PathNaming {
          def toJsonKey(javaKey: String): String
      }

      object DefaultPathNaming extends PathNaming {
          def toJsonKey(javaKey: String) = javaKey
      }
      
      object DefaultDateTimeReads extends Reads[DateTime] {
          def reads(js: JsValue) = {
              JsSuccess(new DateTime(js.as[String], DateTimeZone.UTC))
          }
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
          
          def apply(key: String): DynaJson = {
              val singleRow = () => lookupResult \ ns.toJsonKey(key) match {
                  case r: JsUndefined => new UndefinedDynaJson(r)
                  case r => new DynaJson(r)
              }

              lookupResult match {
                  case JsDefined(js) => key match {
                      case "*" => js match {
                          case a: JsArray => 
                              // rebuild an array with children
                              val children = a.value.map { e => e match {
                                  case a: JsArray => a.value
                                  case o: JsObject => o.values
                                  case _ => Seq[JsValue]()
                              }}.flatten
                              new DynaJson(JsArray(children))
                          case _ => singleRow()
                      }
                      case _ => js match {
                          case a: JsArray => 
                              // rebuild an array with children
                              val children = a.value.filter { e =>
                                  e \ ns.toJsonKey(key) match {
                                      case r: JsUndefined => false
                                      case r => true
                                  }
                              }.map { e => (e \ ns.toJsonKey(key)).as[JsValue] }
                              new DynaJson(JsArray(children))
                          case _ => singleRow()
                      }
                  }
                  case _ =>  singleRow()
              }
          }
          
          def apply(filter: JpPathFilter) = filter.find(this)
      
          def apply(index: Int) = index match {
              case -1 => this
              case _ =>
                  lookupResult(index) match {
                      case r: JsUndefined => new UndefinedDynaJson(r)
                      case r => new DynaJson(r)
                  }
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
          
          def apply(filter: JpPathFilter) = path.apply(key).apply(filter)
      }
      
      abstract class JpExpression {
          def &&(r: JpExpression) = new JpAndExpression(this, r)
          def ||(r: JpExpression) = new JpOrExpression(this, r)
          
          def evaluate(filter: JpPathFilter, path: DynaJson)(implicit PathNaming: PathNaming): Seq[JpResult]
      }
      
      protected class JpAndExpression(l: JpExpression, r: JpExpression) extends JpExpression {
          def evaluate(filter: JpPathFilter, path: DynaJson)(implicit PathNaming: PathNaming) = {
              val lKeys = l.evaluate(filter, path).map{ _.key }.toSet
              r.evaluate(filter, path).filter{ result =>
                  lKeys.contains(result.key)
              }
          }
      }
      
      protected class JpOrExpression(l: JpExpression, r: JpExpression) extends JpExpression {
          def evaluate(filter: JpPathFilter, path: DynaJson)(implicit PathNaming: PathNaming) = {
              (l.evaluate(filter, path) ++ r.evaluate(filter, path)).map { result =>
                  result.key -> result
              }.map(e => e._2).toSeq
          }
      }
      
      abstract class JpBinaryExpression[T: Reads](l: JpPathExpression, r: JpConstantExpression[T]) extends JpExpression {
          def evaluate(filter: JpPathFilter, path: DynaJson)(implicit PathNaming: PathNaming) = {
              val toStringOrDouble = (v: Any) => v match {
                  case i: Int => i.toDouble
                  case i: java.lang.Integer => i.toDouble
                  case d: java.lang.Double => d.toDouble
                  case n: JsNumber => n.as[Double]
                  case s: JsString => s.as[String]
                  case d: DateTime => d
                  case other => other.toString
              }
              
              // either double or string
              val normalize = (l: Any, r: Any) => l match {
                case s: String => r match {
                    case r: DateTime => (new JsString(s).as[T].asInstanceOf[DateTime].getMillis.toDouble, 
                                         r.getMillis.toDouble)
                    case r: Double => (s, r.toString)
                    case _ => (s, r)
                }
                case d: Double => r match {
                    case r: String => (d.toString, r)
                    case _ => (d, r)
                }
            }
              
              val rr = toStringOrDouble(r.evaluate(filter, path).head.key)
              l match {
                  case selection: JpPathExpression => 
                      selection.evaluate(filter, path).filter{ result =>
                          val (lvalue, rvalue) = normalize(toStringOrDouble(result.result), rr)
                          
                          rvalue match {
                              case rvalue: Double => 
                                  _evaluate[Double](lvalue.asInstanceOf[Double], rvalue.asInstanceOf[Double])
                              case rvalue: String => 
                                  _evaluate[String](lvalue.asInstanceOf[String], rvalue.asInstanceOf[String])
                          }
                      }
              }
          }
          
          def _evaluate[T: Ordering](l: T, r: T): Boolean
          
          implicit def compare[T: Ordering](l: T, r: T) = implicitly[Ordering[T]].compare(l, r)
      }
      
      protected class JpConstantExpression[T: Reads](v: T) extends JpExpression {
          def evaluate(filter: JpPathFilter, path: DynaJson)(implicit PathNaming: PathNaming) = 
              Seq(new JpResult(v, null, null))
      }
      
      protected case class JpResult(key: Any, doc: JsValue, result: JsValue)
      
      protected class JpPathExpression(paths: Either[String, Int]*) extends JpExpression with Dynamic {
          def <[R: Reads](r: JpConstantExpression[R]) = new JpBinaryExpression(this, r) {
              override def _evaluate[T: Ordering](l: T, r: T) = compare(l, r) < 0
          }
          
          def <=[R: Reads](r: JpConstantExpression[R]) = new JpBinaryExpression(this, r) {
              override def _evaluate[T: Ordering](l: T, r: T) = compare(l, r) <= 0
          }
          
          def >[R: Reads](r: JpConstantExpression[R]) = new JpBinaryExpression(this, r) {
              override def _evaluate[T: Ordering](l: T, r: T) = compare(l, r) > 0
          }
          
          def >=[R: Reads](r: JpConstantExpression[R]) = new JpBinaryExpression(this, r) {
              override def _evaluate[T: Ordering](l: T, r: T) = compare(l, r) >= 0
          }

          // this works only when you don't have && or || after
          def ==[R: Reads](r: JpConstantExpression[R]) = new JpBinaryExpression(this, r) {
              override def _evaluate[T: Ordering](l: T, r: T) = compare(l, r) == 0
          }

          def ===[R: Reads](r: JpConstantExpression[R]) = new JpBinaryExpression(this, r) {
              override def _evaluate[T: Ordering](l: T, r: T) = compare(l, r) == 0
          }

          def <>[R: Reads](r: JpConstantExpression[R]) = new JpBinaryExpression(this, r) {
              override def _evaluate[T: Ordering](l: T, r: T) = compare(l, r) != 0
          }

          def selectDynamic(path: String) = apply(path)
          
          def applyDynamic(path: String)(index: Int) = apply(path).apply(index)
          
          def apply(path: String) =
              new JpPathExpression((paths.toSeq ++ Seq(Left(path))): _*)
          
          def apply(index: Int) = new JpPathExpression((paths.toSeq ++ Seq(Right(index))): _*)
          
          override def evaluate(filter: JpPathFilter, doc: DynaJson)(implicit PathNaming: PathNaming) = {
              doc.lookupResult match {
                  case JsDefined(jsValue) =>
                      val values = jsValue match {
                          case a: JsArray => a.value.zipWithIndex.view.map {v => (v._2, v._1)}.toSet
                          case m: JsObject => m.fieldSet.asInstanceOf[Set[(String, JsValue)]]
                          case v => Set((null, v))
                      }
                      values.map { row => 
                          var node = new DynaJson(new JsDefined(row._2))
                          paths.foreach{ path =>
                              node = path match {
                                  case Left(s) => node.apply(s)
                                  case Right(i) => node.apply(i)
                              }
                          }
                          (row, node.lookupResult)
                      }.filter { result =>
                          result._2.isInstanceOf[JsDefined]
                      }.map{ result =>
                          JpResult(result._1._1, result._1._2, result._2.get)
                      }.toSeq
                  case _: JsUndefined => Seq.empty
              }
          }
      }
      
      trait JpPathFilter {
          def find(path: DynaJson)(implicit PathNaming: PathNaming): DynaJson
      }
      
      protected class JpListFilter(e: JpExpression) extends JpPathFilter {
          def find(path: DynaJson)(implicit PathNaming: PathNaming): DynaJson = {
            val v = e.evaluate(this, path).map{ e => e.doc }.toSeq
            new DynaJson(JsArray(v))
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
    
      // option filter: returns one or no row
      def ?(e: JpExpression) = new JpPathFilter {
          def find(path: DynaJson)(implicit PathNaming: PathNaming): DynaJson = {
            val v = e.evaluate(this, path).headOption match {
                case Some(e) => JsDefined(e.doc.asInstanceOf[JsValue])
                case None => new JsUndefined("")
            }
            new DynaJson(v)
          }
      }
      
      // wild-card filter: returns an array
      def *(e: JpExpression) = new JpListFilter(e)

      def * = -1
      
      // start from the current node in a filter expression
      def % = new Dynamic {
          def selectDynamic(key: String) = new JpPathExpression(Left(key))
          def applyDynamic(key: String) = new JpPathExpression(Left(key))
      }
}