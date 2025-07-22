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

import mu.KotlinLogging
import org.springframework.core.annotation.AnnotatedElementUtils
import org.springframework.core.annotation.AnnotationUtils
import org.springframework.data.projection.ProjectionFactory
import org.springframework.data.repository.core.RepositoryMetadata
import org.springframework.data.repository.query.Parameters
import org.springframework.data.repository.query.QueryMethod
import org.springframework.data.requery.Annotations
import org.springframework.data.requery.kotlin.annotation.Query
import java.lang.reflect.Method
import kotlin.reflect.KClass

/**
 * RequeryQueryMethod
 *
 * @author debop
 */
class RequeryQueryMethod(val method: Method,
                         val metadata: RepositoryMetadata,
                         val factory: ProjectionFactory) : QueryMethod(method, metadata, factory) {

    companion object {
        private val log = KotlinLogging.logger { }

        private val NATIVE_ARRAY_TYPES: Set<Class<*>> by lazy {
            LinkedHashSet<Class<*>>().apply {
                add(ByteArray::class.java)
                add(CharArray::class.java)
            }
        }
    }

    val entityInformation: RequeryEntityMetadata<*> = DefaultRequeryEntityMetadata.of(domainClass.kotlin)

    init {
        log.debug {
            "Create RequeryQueryMethod. repository=${metadata.repositoryInterface}, queryMethod name=${method.name}, queryMethod=$method"
        }
        check(!isModifyingQuery || !parameters.hasSpecialParameter()) {
            "Modifying query queryMethod must not contains ${Parameters.TYPES}"
        }

        assertParameterNamesInAnnotatedQuery()
    }

    private fun assertParameterNamesInAnnotatedQuery() {

        val query = annotatedQuery

        log.trace { "annotated query=$query" }
    }


    val isAnnotatedQuery: Boolean
        get() = Annotations.findAnnotation(method, Query::class.java) != null

    val isDefaultMethod: Boolean
        get() = method.isDefault

    val isOverridedMethod: Boolean
        get() = method.declaringClass != metadata.repositoryInterface

    internal val returnType: Class<*>
        get() = method.returnType

    val annotatedQuery: String?
        get() {
            val query = getAnnotationValue("value", String::class.java)
            return when {
                query.isNullOrBlank() -> null
                else -> query
            }
        }

    val entityKlass: KClass<*>
        get() = entityInformation.javaType.kotlin

    val entityClass: Class<*>
        get() = entityInformation.javaType


    private fun getAnnotationValue(attribute: String, type: Class<String>): String? {
        return getMergedOrDefaultAnnotationValue(attribute, Query::class.java, type)
    }

    private fun <T> getMergedOrDefaultAnnotationValue(attribute: String,
                                                      annotationType: Class<out Annotation>,
                                                      targetType: Class<T>): T? {
        val annotation = AnnotatedElementUtils.findMergedAnnotation(method, annotationType)

        return annotation?.let {
            targetType.cast(AnnotationUtils.getValue(annotation, attribute))
        } ?: targetType.cast(AnnotationUtils.getDefaultValue(annotationType, attribute))
    }

    override fun createParameters(method: Method): Parameters<*, *> = RequeryParameters(method)

    override fun getParameters(): RequeryParameters = super.getParameters() as RequeryParameters

    override fun isCollectionQuery(): Boolean =
        super.isCollectionQuery() && !NATIVE_ARRAY_TYPES.contains(method.returnType)

}
