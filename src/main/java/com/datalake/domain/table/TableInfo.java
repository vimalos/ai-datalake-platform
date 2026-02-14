package com.datalake.domain.table;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Information about an Iceberg table
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TableInfo {
    private String name;
    private String namespace;
    private String location;
    private List<String> schema;
    private long snapshotId;
    private String createdAt;
    private String lastModified;
}
