                                                                                                  /*
┏━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━┓
┃                                                                                                  ┃
┃                                                    ╭───╮                                         ┃
┃  ╭─────────╮                                       │   │                                         ┃
┃  │   ╭─────╯╭─────────╮╭───╮ ╭───╮╭───╮╌────╮╭────╌┤   │╭───╮╌────╮╭────────╮╭───────╮╭───────╮  ┃
┃  │   ╰─────╮│   ╭─╮   ││   │ │   ││   ╭─╮   ││   ╭─╮   ││   ╭─╮   ││   ╭─╮  ││   ╭───╯│   ╭───╯  ┃
┃  ╰─────╮   ││   │ │   ││   │ │   ││   │ │   ││   │ │   ││   │ │   ││   ├╌╯╌─╯╰─╌ ╰───╮╰─╌ ╰───╮  ┃
┃  ╭─────╯   ││   ╰─╯   ││   ╰─╯   ││   │ │   ││   ╰─╯   ││   │ │   ││   ╰────╮╭───╌   │╭───╌   │  ┃
┃  ╰─────────╯╰─────────╯╰────╌╰───╯╰───╯ ╰───╯╰────╌╰───╯╰───╯ ╰───╯╰────────╯╰───────╯╰───────╯  ┃
┃                                                                                                  ┃
┃    Soundness, version 0.27.0. © Copyright 2023-25 Jon Pretty, Propensive OÜ.                     ┃
┃                                                                                                  ┃
┃    The primary distribution site is:                                                             ┃
┃                                                                                                  ┃
┃        https://soundness.dev/                                                                    ┃
┃                                                                                                  ┃
┃    Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file     ┃
┃    except in compliance with the License. You may obtain a copy of the License at                ┃
┃                                                                                                  ┃
┃        http://www.apache.org/licenses/LICENSE-2.0                                                ┃
┃                                                                                                  ┃
┃    Unless required by applicable law or agreed to in writing,  software distributed under the    ┃
┃    License is distributed on an "AS IS" BASIS,  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,    ┃
┃    either express or implied. See the License for the specific language governing permissions    ┃
┃    and limitations under the License.                                                            ┃
┃                                                                                                  ┃
┗━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━┛
                                                                                                  */
package adversaria

import anticipation.*
import fulminate.*
import proscenium.*

import scala.quoted.*

import language.experimental.captureChecking

object Adversaria:
  def firstField[TargetType <: Product: Type, AnnotationType <: StaticAnnotation: Type]
     (using Quotes)
  :     Expr[CaseField[TargetType, AnnotationType]] =

    import quotes.reflect.*

    val targetType = TypeRepr.of[TargetType]
    val fields = targetType.typeSymbol.caseFields

    fields.flatMap: field =>
      field.annotations.map(_.asExpr).collect:
        case '{$annotation: AnnotationType} => annotation

      . map: annotation =>
          '{CaseField(Text(${Expr(field.name)}), (target: TargetType) =>
              ${'target.asTerm.select(field).asExpr}, $annotation)}
      . reverse
    . head

  def fields[TargetType <: Product: Type, AnnotationType <: StaticAnnotation: Type](using Quotes)
  :     Expr[List[CaseField[TargetType, AnnotationType]]] =

    import quotes.reflect.*

    val targetType = TypeRepr.of[TargetType]
    val fields = targetType.typeSymbol.caseFields

    val elements: List[Expr[CaseField[TargetType, AnnotationType]]] = fields.flatMap: field =>
      val name = Expr(field.name)
      field.annotations.map(_.asExpr).collect:
        case '{$annotation: AnnotationType} => annotation

      . map: annotation =>
          '{CaseField(Text($name), (target: TargetType) => ${'target.asTerm.select(field).asExpr},
              $annotation)}

      . reverse

    Expr.ofList(elements)

  def fieldAnnotations[TargetType: Type](lambda: Expr[TargetType => Any])(using Quotes)
  :     Expr[List[StaticAnnotation]] =

    import quotes.reflect.*

    val targetType = TypeRepr.of[TargetType]

    val field = lambda.asTerm match
      case Inlined(_, _, Block(List(DefDef(_, _, _, Some(Select(_, term)))), _)) =>
        targetType.typeSymbol.caseFields.find(_.name == term).getOrElse:
          panic(m"the member $term is not a case class field")

      case _ =>
        panic(m"the lambda must be a simple reference to a case class field")

    Expr.ofList:
      field.annotations.map(_.asExpr).collect:
        case '{ $annotation: StaticAnnotation } => annotation

  def typeAnnotations[AnnotationType <: StaticAnnotation: Type, TargetType: Type](using Quotes)
  :     Expr[Annotations[AnnotationType, TargetType]] =

    import quotes.reflect.*

    val targetType = TypeRepr.of[TargetType]
    val annotations = targetType.typeSymbol.annotations.map(_.asExpr).collect:
      case '{$annotation: AnnotationType} => annotation

    if annotations.isEmpty
    then
      val typeName = TypeRepr.of[AnnotationType].show
      panic(m"the type ${targetType.show} did not have the annotation $typeName")
    else '{Annotations[AnnotationType, TargetType](${Expr.ofList(annotations)}*)}
