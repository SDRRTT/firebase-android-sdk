// Copyright 2018 Google LLC
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.google.firebase.firestore.model.mutation;

import androidx.annotation.Nullable;
import com.google.firebase.Timestamp;
import com.google.firebase.firestore.model.Document;
import com.google.firebase.firestore.model.DocumentKey;
import com.google.firebase.firestore.model.MaybeDocument;
import com.google.firebase.firestore.model.ObjectValue;
import com.google.firebase.firestore.model.SnapshotVersion;
import com.google.firestore.v1.Value;
import java.util.ArrayList;
import java.util.List;

/**
 * A mutation that creates or replaces the document at the given key with the object value contents.
 */
public final class SetMutation extends Mutation {
  private final ObjectValue value;

  public SetMutation(DocumentKey key, ObjectValue value, Precondition precondition) {
    this(key, value, precondition, new ArrayList<>());
  }

  public SetMutation(
      DocumentKey key,
      ObjectValue value,
      Precondition precondition,
      List<FieldTransform> fieldTransforms) {
    super(key, precondition, fieldTransforms);
    this.value = value;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }

    SetMutation that = (SetMutation) o;
    return hasSameKeyAndPrecondition(that)
        && value.equals(that.value)
        && getFieldTransforms().equals(that.getFieldTransforms());
  }

  @Override
  public int hashCode() {
    int result = keyAndPreconditionHashCode();
    result = result * 31 + value.hashCode();
    return result;
  }

  @Override
  public String toString() {
    return "SetMutation{" + keyAndPreconditionToString() + ", value=" + value + "}";
  }

  @Override
  public MaybeDocument applyToRemoteDocument(
      @Nullable MaybeDocument maybeDoc, MutationResult mutationResult) {
    verifyKeyMatches(maybeDoc);

    // Unlike applyToLocalView, if we're applying a mutation to a remote document the server has
    // accepted the mutation so the precondition must have held.

    SnapshotVersion version = mutationResult.getVersion();

    ObjectValue newData = value;
    if (mutationResult.getTransformResults() != null) {
      List<Value> transformResults =
          serverTransformResults(maybeDoc, mutationResult.getTransformResults());
      newData = transformObject(newData, transformResults);
    }

    return new Document(getKey(), version, newData, Document.DocumentState.COMMITTED_MUTATIONS);
  }

  @Nullable
  @Override
  public MaybeDocument applyToLocalView(
      @Nullable MaybeDocument maybeDoc, @Nullable MaybeDocument baseDoc, Timestamp localWriteTime) {
    verifyKeyMatches(maybeDoc);

    if (!this.getPrecondition().isValidFor(maybeDoc)) {
      return maybeDoc;
    }

    List<Value> transformResults = localTransformResults(localWriteTime, maybeDoc, baseDoc);
    ObjectValue newData = transformObject(value, transformResults);

    SnapshotVersion version = getPostMutationVersion(maybeDoc);
    return new Document(getKey(), version, newData, Document.DocumentState.LOCAL_MUTATIONS);
  }

  /** Returns the object value to use when setting the document. */
  public ObjectValue getValue() {
    return value;
  }
}
