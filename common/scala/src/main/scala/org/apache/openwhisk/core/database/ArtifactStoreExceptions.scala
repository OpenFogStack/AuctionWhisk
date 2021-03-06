/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.openwhisk.core.database

sealed abstract class ArtifactStoreException(message: String) extends Exception(message)

case class NoDocumentException(message: String) extends ArtifactStoreException(message)

case class DatabaseFullException(message: String) extends ArtifactStoreException(message)

case class DocumentConflictException(message: String) extends ArtifactStoreException(message)

case class DocumentTypeMismatchException(message: String) extends ArtifactStoreException(message)

case class DocumentUnreadable(message: String) extends ArtifactStoreException(message)

case class GetException(message: String) extends ArtifactStoreException(message)

case class PutException(message: String) extends ArtifactStoreException(message)

case class DeleteException(message: String) extends ArtifactStoreException(message)

case class QueryException(message: String) extends ArtifactStoreException(message)

sealed abstract class ArtifactStoreRuntimeException(message: String) extends RuntimeException(message)

case class UnsupportedQueryKeys(message: String) extends ArtifactStoreRuntimeException(message)

case class UnsupportedView(message: String) extends ArtifactStoreRuntimeException(message)
