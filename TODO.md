TODO:
3. Add Indices for reading avro by blocks. Cursor vs Index, hmm 🤔
4. ![img.png](doc/img.png) Remove label for the pageSize. Redo pagination to have less pageSize selector.
5. For filters, we need potentially several selectors: 1) field 2) type (equals/contains) 3) value
6. For future, for filters, will be good to have type "between", and have instead of 1 condition for value - two. But to be analyzed how better to do.
7. Add tests

100. debounce??? Should we have it? For now - 100% no.


DONE:
1. ~~Add Spring Boot for DI etc~~ not spring but my own small DI
2. 2. Maybe add small caffeine cache in AvroFileServiceImpl? (maxSize=3-5; expirationTime=???). With complex cache key like: pageSize_pageIndex... something else. Main gold -> update AvroFileServiceImpl to avoid rereading file all the time.