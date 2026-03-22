package com.dkostin.avro_viewer.app.service.api;

import com.dkostin.avro_viewer.app.domain.model.Page;

public interface PageNavigator {

    Page nextPage() throws Exception;

    Page prevPage() throws Exception;

    Page changePageSize(int newPageSize) throws Exception;

    int getPageIndex();

    int getPageSize();

    void setPageSize(int pageSize);

    boolean hasNextPage();
}
