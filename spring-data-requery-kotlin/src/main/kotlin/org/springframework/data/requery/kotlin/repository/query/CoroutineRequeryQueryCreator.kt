/*
 * Copyright 2018 Coupang Ltd.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.data.requery.kotlin.repository.query

import io.requery.query.FieldExpression
import io.requery.query.LogicalCondition
import io.requery.query.NamedExpression
import io.requery.query.element.QueryElement
import io.requery.query.function.Count
import kotlinx.coroutines.runBlocking
import mu.KLogging
import org.springframework.data.domain.Sort
import org.springframework.data.repository.query.ReturnedType
import org.springframework.data.repository.query.parser.AbstractQueryCreator
import org.springframework.data.repository.query.parser.Part
import org.springframework.data.repository.query.parser.Part.Type.AFTER
import org.springframework.data.repository.query.parser.Part.Type.BEFORE
import org.springframework.data.repository.query.parser.Part.Type.BETWEEN
import org.springframework.data.repository.query.parser.Part.Type.CONTAINING
import org.springframework.data.repository.query.parser.Part.Type.ENDING_WITH
import org.springframework.data.repository.query.parser.Part.Type.FALSE
import org.springframework.data.repository.query.parser.Part.Type.GREATER_THAN
import org.springframework.data.repository.query.parser.Part.Type.GREATER_THAN_EQUAL
import org.springframework.data.repository.query.parser.Part.Type.IN
import org.springframework.data.repository.query.parser.Part.Type.IS_EMPTY
import org.springframework.data.repository.query.parser.Part.Type.IS_NOT_EMPTY
import org.springframework.data.repository.query.parser.Part.Type.IS_NOT_NULL
import org.springframework.data.repository.query.parser.Part.Type.IS_NULL
import org.springframework.data.repository.query.parser.Part.Type.LESS_THAN
import org.springframework.data.repository.query.parser.Part.Type.LESS_THAN_EQUAL
import org.springframework.data.repository.query.parser.Part.Type.LIKE
import org.springframework.data.repository.query.parser.Part.Type.NEGATING_SIMPLE_PROPERTY
import org.springframework.data.repository.query.parser.Part.Type.NOT_CONTAINING
import org.springframework.data.repository.query.parser.Part.Type.NOT_IN
import org.springframework.data.repository.query.parser.Part.Type.NOT_LIKE
import org.springframework.data.repository.query.parser.Part.Type.SIMPLE_PROPERTY
import org.springframework.data.repository.query.parser.Part.Type.STARTING_WITH
import org.springframework.data.repository.query.parser.Part.Type.TRUE
import org.springframework.data.repository.query.parser.PartTree
import org.springframework.data.requery.kotlin.NotSupportedException
import org.springframework.data.requery.kotlin.Requery
import org.springframework.data.requery.kotlin.applySort
import org.springframework.data.requery.kotlin.coroutines.CoroutineRequeryOperations
import org.springframework.data.requery.kotlin.unwrap
import org.springframework.data.requery.query.Expressions
import java.util.*
import kotlin.reflect.KClass

/**
 * Repository method name을 분석한 [PartTree] 정보를 바탕으로 Requery [QueryElement]를 생성합니다.
 *
 * @author debop
 * @since 18. 10. 16
 */
open class CoroutineRequeryQueryCreator(val operations: CoroutineRequeryOperations,
                                        val provider: ParameterMetadataProvider,
                                        val returnedType: ReturnedType,
                                        val tree: PartTree)
    : AbstractQueryCreator<Requery, LogicalCondition<out Any, *>>(tree) {

    companion object : KLogging()

    protected val domainKlass: KClass<out Any>
    protected val domainClass: Class<out Any>
    protected val domainClassName: String

    init {
        logger.debug {
            "Create CoroutineRequeryQueryCreator. " +
            "provider=$provider, returnedType=$returnedType, domainClass=${returnedType.domainType}, tree=$tree"
        }

        domainKlass = returnedType.domainType.kotlin
        domainClass = returnedType.domainType
        domainClassName = returnedType.domainType.simpleName
    }

    @Suppress("LeakingThis")
    private val root: Requery by lazy {
        runBlocking {
            createQueryElement(returnedType)
        }
    }

    val parameterExpressions: List<ParameterMetadata<out Any>>
        get() = provider.getExpressions()


    protected open suspend fun createQueryElement(type: ReturnedType): Requery {

        val typeToRead = type.typeToRead
        logger.debug { "Create QueryElement instance. returnedType=$type, typeToRead=$typeToRead" }

        return when {
            tree.isCountProjection -> operations.select(Count.count(type.domainType)).unwrap()
            tree.isExistsProjection -> operations.select(type.domainType.kotlin).unwrap()
            tree.isDelete -> operations.delete(type.domainType.kotlin).unwrap()
            else -> operations.select(type.domainType.kotlin).unwrap()
        }
    }

    override fun create(part: Part, iterator: MutableIterator<Any>): LogicalCondition<out Any, *> {
        logger.trace { "Build new condition ..." }
        return buildWhereCondition(part)
    }

    override fun and(part: Part, base: LogicalCondition<out Any, *>, iterator: MutableIterator<Any>): LogicalCondition<out Any, *> {
        logger.trace { "add AND operator" }

        val condition = buildWhereCondition(part)
        return base.and(condition)
    }

    override fun or(base: LogicalCondition<out Any, *>, criteria: LogicalCondition<out Any, *>): LogicalCondition<out Any, *> {
        logger.trace { "add OR operator" }
        return base.or(criteria)
    }

    override fun complete(criteria: LogicalCondition<out Any, *>?, sort: Sort): Requery {
        return runBlocking { complete(criteria, sort, root) }
    }

    open suspend fun complete(criteria: LogicalCondition<out Any, *>?,
                              sort: Sort,
                              base: Requery): Requery {

        logger.trace { "Complete build query ..." }


        val query = if(criteria != null) base.where(criteria).unwrap() else base
        return query.applySort(domainKlass, sort)
    }

    private fun buildWhereCondition(part: Part): LogicalCondition<out Any, *> {
        return QueryElementBuilder(part).build()
    }

    /**
     * [Part] 정보를 바탕으로 Requery용 [QueryElement]를 빌드합니다.
     */
    private inner class QueryElementBuilder(val part: Part) {

        @Suppress("UNCHECKED_CAST")
        fun build(): LogicalCondition<out Any, *> {
            val property = part.property
            val type = part.type
            val expr: NamedExpression<Any> = NamedExpression.of(property.segment, property.type as Class<Any>)

            logger.debug { "Build Logical condition ... property=$property, type=$type, expr=$expr" }

            return when(type) {
                BETWEEN -> buildBetweenCondition(expr)
                AFTER, GREATER_THAN -> buildGreaterThanCondition(expr)
                GREATER_THAN_EQUAL -> buildGreaterThanEqualCondition(expr)
                BEFORE, LESS_THAN -> buildLessThanCondition(expr)
                LESS_THAN_EQUAL -> buildLessThanEqualCondition(expr)
                IS_NULL -> expr.isNull
                IS_NOT_NULL -> expr.notNull()
                NOT_IN, IN -> buildInCondition(expr, type)
                STARTING_WITH, ENDING_WITH, CONTAINING, NOT_CONTAINING -> buildStringPatternCondition(expr, type)
                LIKE, NOT_LIKE -> buildLikeCondition(expr, type)
                TRUE -> expr.eq(true)
                FALSE -> expr.eq(false)
                SIMPLE_PROPERTY -> buildSimplePropertyCondition(expr)
                NEGATING_SIMPLE_PROPERTY -> buildNegatingSimplePropertyCondition(expr)
                IS_EMPTY, IS_NOT_EMPTY -> throw NotSupportedException("Not supported keyword $type")
                else -> throw NotSupportedException("Not supported keyword $type")
            }
        }

        private fun buildBetweenCondition(expr: NamedExpression<Any>): LogicalCondition<out Any, *> {
            return expr.between(
                provider.next(part, Comparable::class.java).value,
                provider.next(part, Comparable::class.java).value
            )
        }

        private fun buildGreaterThanCondition(expr: NamedExpression<Any>): LogicalCondition<out Any, *> {
            return expr.greaterThan(provider.next(part, Comparable::class.java).value)
        }

        private fun buildGreaterThanEqualCondition(expr: NamedExpression<Any>): LogicalCondition<out Any, *> {
            return expr.greaterThanOrEqual(provider.next(part, Comparable::class.java).value)
        }

        private fun buildLessThanCondition(expr: NamedExpression<Any>): LogicalCondition<out Any, *> {
            return expr.lessThan(provider.next(part, Comparable::class.java).value)
        }

        private fun buildLessThanEqualCondition(expr: NamedExpression<Any>): LogicalCondition<out Any, *> {
            return expr.lessThanOrEqual(provider.next(part, Comparable::class.java).value)
        }

        private fun buildInCondition(expr: NamedExpression<Any>, type: Part.Type): LogicalCondition<out Any, *> {
            val values = provider.next(part, Collection::class.java).value
            logger.debug { "IN, NOT IN values type=${values?.javaClass?.simpleName}, values=${values}" }

            return values?.let { buildInConditionForValues(expr, type, it) }
                ?: error("Not provided in columns")
        }

        private fun buildInConditionForValues(expr: NamedExpression<Any>, type: Part.Type, values: Any): LogicalCondition<out Any, *> {
            return when(values) {
                is Iterable<*> -> buildInConditionForIterable(expr, type, values)
                is Array<*> -> buildInConditionForArray(expr, type, values)
                is IntArray, is LongArray, is ShortArray, is CharArray, 
                is ByteArray, is FloatArray, is DoubleArray -> buildInConditionForPrimitiveArray(expr, type, values)
                else -> if(type == IN) expr.`in`(values) else expr.notIn(values)
            }
        }

        private fun buildInConditionForIterable(expr: NamedExpression<Any>, type: Part.Type, values: Iterable<*>): LogicalCondition<out Any, *> {
            logger.trace { "value is iterable. values=${values.toList()}" }
            return if(type == IN) Expressions.`in`(expr, values.toList())
            else Expressions.notIn(expr, values.toList())
        }

        private fun buildInConditionForArray(expr: NamedExpression<Any>, type: Part.Type, values: Array<*>): LogicalCondition<out Any, *> {
            logger.trace { "value is array. values=${values.toList()}" }
            return if(type == IN) Expressions.`in`(expr, values.toList())
            else Expressions.notIn(expr, values.toList())
        }

        private fun buildInConditionForPrimitiveArray(expr: NamedExpression<Any>, type: Part.Type, values: Any): LogicalCondition<out Any, *> {
            logger.trace { "value is primitive array. values=${values}" }
            val valuesList = when(values) {
                is IntArray -> values.toList()
                is LongArray -> values.toList()
                is ShortArray -> values.toList()
                is CharArray -> values.toList()
                is ByteArray -> values.toList()
                is FloatArray -> values.toList()
                is DoubleArray -> values.toList()
                else -> error("Unsupported primitive array type")
            }
            return if(type == IN) Expressions.`in`(expr, valuesList)
            else Expressions.notIn(expr, valuesList)
        }

        private fun buildStringPatternCondition(expr: NamedExpression<Any>, type: Part.Type): LogicalCondition<out Any, *> {
            val value = provider.next(part, String::class.java).value.toString()
            return when(type) {
                STARTING_WITH -> expr.like("$value%")
                ENDING_WITH -> expr.like("%$value")
                CONTAINING -> expr.like("%$value%")
                NOT_CONTAINING -> expr.notLike("%$value%")
                else -> error("Unsupported string pattern type: $type")
            }
        }

        private fun buildLikeCondition(expr: NamedExpression<Any>, type: Part.Type): LogicalCondition<out Any, *> {
            var value = provider.next(part, String::class.java).value.toString()
            if(shouldIgnoreCase()) {
                value = value.toUpperCase()
            }
            if(!value.startsWith("%") && !value.endsWith("%")) {
                value = "%$value%"
            }

            return when(type) {
                LIKE -> expr.upperIfIgnoreCase().like(value)
                else -> expr.upperIfIgnoreCase().notLike(value)
            }
        }

        private fun buildSimplePropertyCondition(expr: NamedExpression<Any>): LogicalCondition<out Any, *> {
            val simpleExpr = provider.next<Any>(part)
            return when {
                simpleExpr.isNullParameter -> expr.isNull
                else -> {
                    val value = if(shouldIgnoreCase()) simpleExpr.value!!.toUpperCase() else simpleExpr.value
                    expr.upperIfIgnoreCase().equal(value)
                }
            }
        }

        private fun buildNegatingSimplePropertyCondition(expr: NamedExpression<Any>): LogicalCondition<out Any, *> {
            val simpleExpr = provider.next<Any>(part)
            val value = if(shouldIgnoreCase()) simpleExpr.value!!.toUpperCase() else simpleExpr.value
            return expr.upperIfIgnoreCase().notEqual(value)
        }

        private fun <T> FieldExpression<T>.upperIfIgnoreCase(): FieldExpression<T> {
            return when(part.shouldIgnoreCase()) {
                Part.IgnoreCaseType.ALWAYS -> {
                    check(this.canUppserCase()) { "Unable to ignore case of ${classType.name}" }
                    this.function("Upper")
                }
                Part.IgnoreCaseType.WHEN_POSSIBLE -> {
                    if(this.canUppserCase()) this.function("Upper") else this
                }
                Part.IgnoreCaseType.NEVER -> this
                else -> this
            }
        }

        private fun FieldExpression<*>.canUppserCase(): Boolean = this.classType == String::class.java

        fun shouldIgnoreCase(): Boolean = part.shouldIgnoreCase() != Part.IgnoreCaseType.NEVER

        fun Any.toUpperCase(): String = this.toString().toUpperCase(Locale.getDefault())

    }
}
