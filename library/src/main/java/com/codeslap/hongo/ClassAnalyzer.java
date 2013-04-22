/*
 * Copyright 2013 CodeSlap
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.codeslap.hongo;

import java.lang.reflect.Field;
import java.lang.reflect.ParameterizedType;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;

public class ClassAnalyzer {
  public static Collection<HasManySpec> getHasManySpecs(Class<?> objectType,
                                                        Collection<ColumnField> columnFields) {
    Collection<HasManySpec> specs = new ArrayList<HasManySpec>();
    for (ColumnField columnField : columnFields) {
      HasManySpec hasManySpec = searchForHasMany(columnField, objectType);
      if (hasManySpec != null) {
        specs.add(hasManySpec);
      }
    }
    return specs;
  }

  private static HasManySpec searchForHasMany(ColumnField columnField, Class<?> objectType) {
    HasMany hasMany = columnField.getAnnotation(HasMany.class);
    if (hasMany == null) {
      return null;
    }
    Class<?> collectionClass = columnField.getGenericType();

    if (!collectionClass.isAnnotationPresent(Belongs.class)) {
      throw new IllegalStateException(
          "When defining a HasMany relation you must specify a Belongs annotation in the child class");
    }
    Belongs belongs = collectionClass.getAnnotation(Belongs.class);
    if (belongs.to() != objectType) {
      throw new IllegalStateException(
          "Belongs class points to " + belongs.to() + " but should point to " + objectType);
    }

    Belongs thisBelongsTo = objectType.getAnnotation(Belongs.class);
    if (thisBelongsTo != null && thisBelongsTo.to() == collectionClass) {
      throw new IllegalStateException(
          "Cyclic has-many relations not supported. Use many-to-many instead: " +
              collectionClass.getSimpleName() + " belongs to " + objectType.getSimpleName() + " and viceversa");
    }

    return new HasManySpec(objectType, columnField.getName(), collectionClass);
  }

  public static Collection<? extends ManyToManySpec> getManyToManySpecs(
      ReflectDataObject dataObjectA, Class<?> objectType, Set<Class<?>> graph,
      Collection<ColumnField> columnFields) {
    Collection<ManyToManySpec> manyToManySpecs = new ArrayList<ManyToManySpec>();
    for (ColumnField columnField : columnFields) {
      if (columnField.getType() == List.class) {
        ManyToManySpec manyToManySpec = searchForManyToMany(dataObjectA, objectType, graph,
            columnField);
        if (manyToManySpec != null) {
          manyToManySpecs.add(manyToManySpec);
        }
      }
    }
    return manyToManySpecs;
  }

  private static ManyToManySpec searchForManyToMany(ReflectDataObject dataObjectA,
                                                    Class<?> objectType, Set<Class<?>> graph,
                                                    ColumnField field) {
    ManyToMany manyToMany = field.getAnnotation(ManyToMany.class);
    if (manyToMany == null || graph.contains(objectType)) {
      return null;
    }
    Class<?> collectionClass = field.getGenericType();

    boolean relationExists = false;
    ManyToMany manyToManyColl;
    for (Field collField : collectionClass.getDeclaredFields()) {
      manyToManyColl = collField.getAnnotation(ManyToMany.class);
      if (manyToManyColl != null && collField.getType() == List.class) {
        ParameterizedType parameterizedTypeColl = (ParameterizedType) collField.getGenericType();
        Class<?> selfClass = (Class<?>) parameterizedTypeColl.getActualTypeArguments()[0];
        if (selfClass == objectType) {
          relationExists = true;
          break;
        }
      }
    }

    if (!relationExists) {
      throw new IllegalStateException(
          "When defining a ManyToMany relation both classes must use the ManyToMany annotation");
    }

    if (graph.add(objectType)) {
      ReflectDataObject collDataObject = new ReflectDataObject(collectionClass, graph);
      return new ManyToManySpec(dataObjectA, field.getName(), collDataObject);
    }
    return null;
  }
}