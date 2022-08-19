# springbooster: auto generation for springboot app

<a href="CHANGELOG.md">ChangeLog</a>

### JPA
  - Query
    - Single table
      - `if !projection && !(large resultset) && !(paging/sorting)` `JPA Built-in Query` 
      - `if projection || (!(large resultset) && paging/static sorting/filter)`  `QueryDSL`
      - `if large resultset || dynamic sorting/filter`  `CustomizedRepo`
      - > QueryDSL not support limit in subquery for now
    - Join table
      - `if static sorting/filter` `QueryDSL`
      - `else NativeSQL`
      - if large resultset, and Read frequent, consider creating a redundant table
  - Insert/Update/Delete
    - `insert` `JPA Built-in Query`
    - `if projection update` `QueryDSL`
      - > QueryDSL not support update conjunction join for now, use customizedRepo instead 
    - `transactional service`

### Unit Test
  - save/saveall: clone detached individual list as no join
  - With Query: Build a full relation graph and store in DB

### SpringBoot Validation
  - inplace configuration