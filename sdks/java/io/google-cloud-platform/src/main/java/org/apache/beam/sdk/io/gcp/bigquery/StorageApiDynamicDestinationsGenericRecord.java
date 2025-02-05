/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.beam.sdk.io.gcp.bigquery;

import com.google.api.services.bigquery.model.TableRow;
import com.google.api.services.bigquery.model.TableSchema;
import com.google.protobuf.Descriptors.Descriptor;
import com.google.protobuf.Message;
import org.apache.avro.Schema;
import org.apache.avro.generic.GenericRecord;
import org.apache.beam.sdk.io.gcp.bigquery.BigQueryServices.DatasetService;
import org.apache.beam.sdk.schemas.utils.AvroUtils;
import org.apache.beam.sdk.transforms.SerializableFunction;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;

/** Storage API DynamicDestinations used when the input is a Beam Row. */
class StorageApiDynamicDestinationsGenericRecord<T, DestinationT extends @NonNull Object>
    extends StorageApiDynamicDestinations<T, DestinationT> {

  private final SerializableFunction<AvroWriteRequest<T>, GenericRecord> toGenericRecord;
  private final SerializableFunction<@Nullable TableSchema, Schema> schemaFactory;

  StorageApiDynamicDestinationsGenericRecord(
      DynamicDestinations<T, DestinationT> inner,
      SerializableFunction<@Nullable TableSchema, Schema> schemaFactory,
      SerializableFunction<AvroWriteRequest<T>, GenericRecord> toGenericRecord) {
    super(inner);
    this.toGenericRecord = toGenericRecord;
    this.schemaFactory = schemaFactory;
  }

  @Override
  public MessageConverter<T> getMessageConverter(
      DestinationT destination, DatasetService datasetService) throws Exception {
    return new GenericRecordConverter(destination);
  }

  class GenericRecordConverter implements MessageConverter<T> {

    final com.google.cloud.bigquery.storage.v1.TableSchema protoTableSchema;
    final Schema avroSchema;
    final TableSchema bqTableSchema;
    final Descriptor descriptor;

    GenericRecordConverter(DestinationT destination) throws Exception {
      avroSchema = schemaFactory.apply(getSchema(destination));
      bqTableSchema = BigQueryUtils.toTableSchema(AvroUtils.toBeamSchema(avroSchema));
      protoTableSchema =
          AvroGenericRecordToStorageApiProto.protoTableSchemaFromAvroSchema(avroSchema);
      descriptor = TableRowToStorageApiProto.getDescriptorFromTableSchema(protoTableSchema, true);
    }

    @Override
    @SuppressWarnings("nullness")
    public StorageApiWritePayload toMessage(T element) {
      Message msg =
          AvroGenericRecordToStorageApiProto.messageFromGenericRecord(
              descriptor, toGenericRecord.apply(new AvroWriteRequest<>(element, avroSchema)));
      return new AutoValue_StorageApiWritePayload(msg.toByteArray(), null);
    }

    @Override
    public TableRow toTableRow(T element) {
      return BigQueryUtils.convertGenericRecordToTableRow(
          toGenericRecord.apply(new AvroWriteRequest<>(element, avroSchema)), bqTableSchema);
    }

    @Override
    public com.google.cloud.bigquery.storage.v1.TableSchema getTableSchema() {
      return protoTableSchema;
    }

    @Override
    public StorageApiWritePayload toMessage(TableRow tableRow, boolean respectRequired)
        throws Exception {
      throw new RuntimeException("Not supported");
    }
  }
}
