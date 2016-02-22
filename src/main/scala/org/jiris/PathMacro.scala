package org.jiris

import scala.language.dynamics
import play.api.libs.json.Json._
import play.api.libs.json._
import org.joda.time.DateTime
import org.joda.time.DateTimeZone
import scala.reflect.runtime.universe._
import language.experimental.macros
import reflect.macros.blackbox.Context
import org.jiris.JsonPath.PathNaming

object PathMacro {
    def $(param: Any): JsLookupResult = macro jsonpath_impl[JsLookupResult]
    
    def pathMacro(param: Any): Unit = macro jsonpath_impl[Unit]

    def jsonpath_impl[R](c: Context)(param: c.Expr[Any]): c.Expr[R] = {
        import c.universe._
        
        val func = (module: String, fn: String) =>
            Select(Ident(c.mirror.staticModule(module)), TermName(fn))
            
        val jsValueToJsLookupFunc = func("play.api.libs.json.JsValue", "jsValueToJsLookup")
        val applyFunc = func("org.jiris.JsonPath.DynaJson", "apply")

        var pathNamingVar: List[Tree] = List.empty
        
        def \(a: Tree, y: List[Tree]) = {
            Apply(applyFunc, List(traverse(a), y.head))
        }

        def traverse(tree: Tree): Tree = {
            tree match {
                case Apply(x, y) =>
                    x match {
                        case Select(a, b) =>
                            if (b.toString == "selectDynamic") {
                                a match {
                                    case Apply(x1, y1) =>
                                        x1 match {
                                            case Select(a1, b1) =>
                                                if (b1.toString == "$") {
                                                    a1 match {
                                                        case Apply(x2, y2) =>
                                                            x1 match {
                                                                case Select(a2, b2) =>
                                                                    val variable = Select(Apply(jsValueToJsLookupFunc, y2), "result")
                                                                    pathNamingVar = y1
                                                                    Apply(applyFunc, List(variable, y.head))
                                                                case _ =>
                                                                    \(a, y)
                                                            }
                                                        case _ =>
                                                            \(a, y)
                                                    }
                                                } else {
                                                    \(a, y)
                                                }
                                            case _ => \(a, y)
                                        }
                                    case _ => \(a, y)
                                }
                            } else if (b.toString == "apply") {
                                a match {
                                    case Apply(x1, y1) =>
                                        x1 match {
                                            case Select(a1, b1) =>
                                                if (b1.toString == "applyDynamic") {
                                                    val variable = \(a1, y1)
                                                    Apply(applyFunc, List(traverse(variable), y.head))
                                                } else {
                                                    Apply(Select(traverse(a), b), y)
                                                }
                                            case _ =>
                                                Apply(Select(traverse(a), b), y)
                                        }
                                    case _ =>
                                        Apply(Select(traverse(a), b), y)
                                }
                            } else {
                                Apply(Select(traverse(a), b), y)
                            }
                        case _ =>
                            Apply(x, y)
                    }
                case rest =>
                    println(rest)
                    rest
            }
        }
        c.Expr(traverse(param.tree))
    }
}