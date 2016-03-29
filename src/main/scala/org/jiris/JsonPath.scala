package org.jiris

import scala.language.dynamics
import language.experimental.macros
import reflect.macros.blackbox.Context

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

        def apply(lookupResult: JsLookupResult, key: String)(implicit ns: PathNaming): JsLookupResult = {
            val singleRow = () => lookupResult \ ns.toJsonKey(key)

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
                            JsDefined(JsArray(children))
                        case o: JsObject => JsDefined(JsArray(o.values.toSeq))
                        case _ => new JsUndefined("")
                    }
                    case _ => js match {
                        case a: JsArray => 
                            // rebuild an array with children
                            val children = a.value.filter { e =>
                                e \ key match {
                                    case r: JsUndefined => false
                                    case r => true
                                }
                            }.map { e => (e \ ns.toJsonKey(key)).as[JsValue] }
                            JsDefined(JsArray(children))
                        case _ => singleRow()
                    }
                }
                case _ =>  singleRow()
            }
        }

        def apply(lookupResult: JsLookupResult, index: Int)(implicit ns: PathNaming): JsLookupResult = index match {
            case -1 => lookupResult
            case _ => lookupResult(index)
        }

        def apply(lookupResult: JsLookupResult, filter: JpPathFilter)(implicit ns: PathNaming) = filter.find(lookupResult)
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
                        case o: JsObject => new DynaJson(JsArray(o.values.toSeq))
                        case _ => new UndefinedDynaJson(new JsUndefined(""))
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
        
        def apply(filter: JpPathFilter) = filter.find(lookupResult)
    
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
        
        def getOrElse[T: Reads](e: => T) = this match {
            case _: JpUndefined => e
            case _ => lookupResult.as[T]
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
        
        def evaluate(filter: JpPathFilter, path: JsLookupResult)(implicit pathNaming: PathNaming): Seq[JpResult]
    }
    
    protected class JpAndExpression(l: JpExpression, r: JpExpression) extends JpExpression {
        def evaluate(filter: JpPathFilter, path: JsLookupResult)(implicit pathNaming: PathNaming) = {
            val lKeys = l.evaluate(filter, path).map{ _.key }.toSet
            r.evaluate(filter, path).filter{ result =>
                lKeys.contains(result.key)
            }
        }
    }
    
    protected class JpOrExpression(l: JpExpression, r: JpExpression) extends JpExpression {
        def evaluate(filter: JpPathFilter, path: JsLookupResult)(implicit pathNaming: PathNaming) = {
            (l.evaluate(filter, path) ++ r.evaluate(filter, path)).map { result =>
                result.key -> result
            }.map(e => e._2).toSeq
        }
    }
    
    abstract class JpBinaryExpression[T: Reads](l: JpPathExpression, r: JpConstantExpression[T]) extends JpExpression {
        def evaluate(filter: JpPathFilter, path: JsLookupResult)(implicit pathNaming: PathNaming) = {
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
        def evaluate(filter: JpPathFilter, path: JsLookupResult)(implicit PathNaming: PathNaming) = 
            Seq(new JpResult(v, null, null))
    }
    
    protected case class JpResult(key: Any, doc: JsValue, result: JsValue)
    
    object JpPathExpression {
        def apply(key: String) = new Object() {
            def test = "hello"
        }
//        def apply(key: String): JpPathExpression = new JpPathExpression(Left(key))
        
        def apply(expr: JpPathExpression, key: String) = expr(key)

        def apply(expr: JpPathExpression, index: Int) = expr(index)
    }
    
    class JpPathExpression(paths: Either[String, Int]*) extends JpExpression with Dynamic {
        def test = new JpPathExpression(Left("Hello"))
        
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
        
        override def evaluate(filter: JpPathFilter, doc: JsLookupResult)(implicit ns: PathNaming) = {
            doc match {
                case JsDefined(jsValue) =>
                    val values = jsValue match {
                        case a: JsArray => a.value.zipWithIndex.view.map {v => (v._2, v._1)}.toSet
                        case m: JsObject => m.fieldSet.asInstanceOf[Set[(String, JsValue)]]
                        case v => Set((null, v))
                    }
                    values.map { row => 
                        var node: JsLookupResult = new JsDefined(row._2)
                        paths.foreach{ path =>
                            node = path match {
                                case Left(s) => DynaJson.apply(node, s)
                                case Right(i) => DynaJson.apply(node, i)
                            }
                        }
                        (row, node)
                    }.filter { result =>
                        result._2.isInstanceOf[JsDefined]
                    }.map{ result =>
                        JpResult(result._1._1, result._1._2, result._2.get)
                    }.toSeq
                case _: JsUndefined => Seq.empty
            }
        }
    }
    
    implicit def DynaJsonToJsLookupResult(doc: DynaJson) = doc.lookupResult

    implicit def intToExpression(i: Int) = new JpConstantExpression(i)

    implicit def doubleToExpression(d: Double) = new JpConstantExpression(d)

    implicit def stringToExpression(s: String) = new JpConstantExpression(s)

    implicit def dateTimeToExpression(dt: DateTime) = new JpConstantExpression(dt)

    implicit class JpJsValueWrapper(jsValue: JsValue) {
        def $(implicit PathNaming: PathNaming) = new DynaJson(jsValue)
    }
  
    trait JpPathFilter {
        def find(path: JsLookupResult)(implicit PathNaming: PathNaming): DynaJson
    }
    
    protected class JpListFilter(e: JpExpression) extends JpPathFilter {
        def find(path: JsLookupResult)(implicit PathNaming: PathNaming): DynaJson = {
            val v = e.evaluate(this, path).map{ e => e.doc }.toSeq
            new DynaJson(JsArray(v))
        }
    }

    // option filter: returns one or no row
    def ?(e: JpExpression) = new JpPathFilter {
        def find(path: JsLookupResult)(implicit ns: PathNaming): DynaJson = {
          val v = e.evaluate(this, path).headOption match {
              case Some(e) => JsDefined(e.doc.asInstanceOf[JsValue])
              case None => new JsUndefined("")
          }
          new DynaJson(v)
        }
    }
    
    def ?(e: Any) = new Object()
    
    // wild-card filter: returns an array
    def *(e: JpExpression) = new JpListFilter(e)

    def * = -1
    
    // start from the current node in a filter expression
    def % = new Dynamic {
        def selectDynamic(key: String) = new JpPathExpression(Left(key))
        def applyDynamic(key: String) = new JpPathExpression(Left(key))
    }

    def $(param: Any): Any = macro jsonpath_impl[Any]
    
    def jsonpath_impl[R](c: Context)(param: c.Expr[Any]): c.Expr[R] = {
        import c.universe._
        
        val func = (module: String, fn: String) =>
            Select(Ident(c.mirror.staticModule(module)), TermName(fn))
            
        val jsValueToJsLookupFunc = func("play.api.libs.json.JsValue", "jsValueToJsLookup")
        val djApplyFunc = func("org.jiris.JsonPath.DynaJson", "apply")
        val jpeApplyFunc = func("org.jiris.JsonPath.JpPathExpression", "apply")

        object xformer extends Transformer {
            var inCondition = false
            
            implicit class TreeWrapper(a: Tree) {
                def \(y: List[Tree]) = {
                    Apply(djApplyFunc, List(transform(a), transform(y.head)))
                }

                def /(y: List[Tree]) = {
                    Apply(jpeApplyFunc, List(transform(a), transform(y.head)))
                }
            }
            
            implicit class SelectWrapper(s: Select) {
                def \(y: List[Tree]) = {
                    Apply(djApplyFunc, List(s, transform(y.head)))
                }
            }
            
            override def transform(tree: Tree): Tree = {
                tree match {
                    case Apply(Select(Apply(Select(Apply(x2, y2), TermName("$")), y1), TermName("selectDynamic")), y) =>
                        Select(Apply(jsValueToJsLookupFunc, y2), TermName("result")) \ y // js.$.store
//                    case Apply(Select(Select(Ident(m), TermName("$percent")), TermName("selectDynamic")), y)
//                            if m.toTermName == TermName("JsonPath") =>
//                        super.transform(tree) // JsonPath.%
                    case Apply(Select(Ident(m), TermName("$qmark")), y)
                           if m.toTermName == TermName("JsonPath") =>
                        inCondition = true
                        val t = Apply(Select(Ident(m), TermName("$qmark")), y.map( y => transform(y)))
                        inCondition = false
                        t
                    case Apply(Select(Ident(m), TermName("$times")), y)
                           if m.toTermName == TermName("JsonPath") =>
                        inCondition = true
                        val t = Apply(Select(Ident(m), TermName("$times")), y.map( y => transform(y)))
                        inCondition = false
                        t
                    case Apply(Select(Select(Ident(m), TermName("$percent")), TermName("selectDynamic")), y)
                            if m.toTermName == TermName("JsonPath") =>
                        Apply(jpeApplyFunc, y.map( y => transform(y)))
//                        Apply(jpeApplyFunc, List(transform(y.head)))
                    case Apply(Select(a, TermName("selectDynamic")), y) =>
                        if(inCondition) a / y
                        else a \ y // book.title
                    case Apply(Select(Apply(Select(a1, TermName("applyDynamic")), y1), TermName("apply")), y) =>
                        if(inCondition) a1 / y1 / y
                        else a1 \ y1 \ y // store.book(0)
                    case _ => super.transform(tree)
                }
            }
        }
        c.Expr(xformer.transform(param.tree))
    }
}