package com.dkostin.avro_viewer.app.service.api;

import com.dkostin.avro_viewer.app.domain.model.Page;

import java.util.OptionalInt;
import java.util.OptionalLong;

public interface PageNavigator {

    Page nextPage() throws Exception;

    Page prevPage() throws Exception;

    Page changePageSize(int newPageSize) throws Exception;

    int getPageIndex();

    int getPageSize();

    void setPageSize(int pageSize);

    boolean hasNextPage();

    OptionalLong totalRecords();

    OptionalInt totalPages();
}
