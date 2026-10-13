# 레거시 문항 은행 비즈니스 규칙 (교차 검증용)

- 읽은 파일: `legacy/item-bank-php/search.php`, `register.php`, `units.php`, `inc/db.php` (`vendor/` 는 읽지 않음, `docs/` 는 참조하지 않음)
- 기준: 코드가 사실이다. 주석과 코드가 다르면 코드를 따르고 그 사실을 "비고"에 적는다.
- 경로는 모두 `legacy/item-bank-php/` 기준.

## A. 검색 조건 조합 (search.php)

### CX-01 검색 조건은 모두 AND 로 결합한다
- 근거: `search.php:44`, `search.php:98`, `search.php:118`
- 코드:
  ```php
  $where    = " WHERE 1=1";
  $where .= " AND (title LIKE ? OR stem LIKE ?)";
  $where .= " AND unit_code = ?";
  ```
- 비고: 키워드 · 단원 · 난이도 · 태그 어느 조합이든 OR 로 묶이는 경우는 없다(키워드 내부의 제목/지문만 OR).

### CX-02 키워드는 앞뒤 공백을 제거하고 100자를 넘으면 100자로 자른다(경고만 표시)
- 근거: `search.php:85-89`
- 코드:
  ```php
  $q = trim($q);
  if (mb_strlen($q, 'UTF-8') > 100) {
      $q = mb_substr($q, 0, 100, 'UTF-8');
  ```

### CX-03 키워드는 제목(title) 또는 지문(stem)에 대한 부분 일치(LIKE %q%)이며, `%` `_` 는 이스케이프하지 않고 와일드카드로 그대로 동작한다
- 근거: `search.php:94-98`
- 코드:
  ```php
  if (strpos($q, '%') !== false || strpos($q, '_') !== false) {
      $warnings[] = '키워드의 % 와 _ 는 와일드카드로 처리됩니다.';
  ...
  $where .= " AND (title LIKE ? OR stem LIKE ?)";
  ```
- 비고: 빈 키워드(`''`)면 조건 자체를 추가하지 않는다(`search.php:90`). 한 글자 키워드도 막지 않고 경고만 한다(`search.php:91-93`).

### CX-04 단원 필터는 단원 코드와의 정확 일치이며, 입력값을 대문자로 바꾸지 않는다
- 근거: `search.php:109-120`
- 코드:
  ```php
  $unit = trim($unit);
  if ($unit !== strtoupper($unit)) {
      $warnings[] = '단원 코드는 대문자로 입력하세요. (입력값 그대로 조회합니다)';
  ...
  $where .= " AND unit_code = ?";
  ```
- 비고: 대소문자 구분 여부는 DB 콜레이션에 달려 있어 이 폴더 코드만으로는 확정할 수 없다.

### CX-05 단원 코드 형식이 틀렸거나 등록되지 않은 코드여도 조회는 그대로 수행하고 경고만 남긴다
- 근거: `search.php:111-114`, `search.php:141-143`
- 코드:
  ```php
  // 형식이 달라도 그대로 조회한다 (결과는 대개 0건)
  $warnings[] = '단원 코드 형식이 올바르지 않습니다. (예: M5-1)';
  ...
  $warnings[] = '등록되지 않은 단원 코드입니다: ' . $unit;
  ```
- 비고: 형식 정규식은 `/^[A-Za-z][0-9]{1,2}-[0-9]{1,2}$/` (`search.php:111`).

### CX-06 난이도가 비어 있으면(전체 선택) 난이도 5 문항을 제외한다 (`level < 5`)
- 근거: `search.php:213-216`
- 코드:
  ```php
  // 난이도 값이 비어 있으면 전체 난이도 검색 (1~5 모두 포함)
  if ($level == '') {
      $where .= " AND level < 5";
  ```
- 비고: **주석과 코드가 다르다.** 주석은 "1~5 모두 포함"이지만 코드는 5 를 뺀다. 코드를 기준으로 기록했다. 요약에도 난이도 항목이 붙지 않아(`search.php:214-216` 에 `$summary[]` 없음) 화면에서는 이 제외가 드러나지 않는다. 또 `$level == ''` 는 느슨한 비교다.

### CX-07 난이도가 `1`~`5` 한 자리 숫자면 그 값과 정확히 일치하는 문항만 찾는다
- 근거: `search.php:217-222`
- 코드:
  ```php
  else if (preg_match('/^[1-5]$/', $level)) {
      $where .= " AND level = ?";
      $values[] = (int)$level;
  ```
- 비고: 난이도 5 는 이렇게 명시하면 조회된다. 즉 5 는 "명시 선택 시에만" 나온다(CX-06 과 함께 볼 것).

### CX-08 난이도가 1~5 밖의 값이면 경고를 내되, 정수로 바꿔 그대로 비교한다(오류로 막지 않음)
- 근거: `search.php:223-230`
- 코드:
  ```php
  $warnings[] = '난이도는 1~5 사이여야 합니다.';
  $where .= " AND level = ?";
  $values[] = (int)$level;
  ```
- 비고: `abc` 같은 비숫자는 `(int)` 캐스트로 0 이 되어 `level = 0` 으로 조회된다.

### CX-09 태그 필터는 태그 이름 정확 일치 1개이며, 그 태그가 달린 문항만 남긴다. 이름은 50자로 자른다
- 근거: `search.php:235-248`
- 코드:
  ```php
  $tag = trim($tag);
  if (mb_strlen($tag, 'UTF-8') > 50) {
      $tag = mb_substr($tag, 0, 50, 'UTF-8');
  ...
  $where .= " AND EXISTS (SELECT 1 FROM item_tag it JOIN tag t ON t.id = it.tag_id"
          . " WHERE it.item_id = v_item_public.id AND t.name = ?)";
  ```
- 비고: 부분 일치는 지원하지 않는다(`search.php:237-239` 경고). 등록되지 않은 태그도 조회는 하고 경고만 낸다(`search.php:258-260`).

### CX-10 각 검색 파라미터가 배열로 오면 첫 값만 쓴다
- 근거: `search.php:53-55` (같은 패턴이 `unit` `level` `tag` `sort` `dir` `page` 에 반복: `search.php:57-80`)
- 코드:
  ```php
  if (isset($params['q'])) {
      $q = is_array($params['q']) ? (string)reset($params['q']) : (string)$params['q'];
  ```

### CX-11 검색은 테이블이 아니라 공개 문항 뷰 `v_item_public` 에서만 조회한다
- 근거: `search.php:521-522`, `search.php:526`
- 코드:
  ```php
  $select = "SELECT id, title, unit_code, level, tag_names, created_at";
  $from   = " FROM v_item_public";
  ```
- 비고: 건수 쿼리도 같은 `FROM` 과 `WHERE` 를 쓴다(`search.php:526`). **이 뷰의 정의는 `legacy/item-bank-php` 안에 없어** 어떤 상태의 문항이 걸러지는지는 이 폴더의 코드만으로는 확인되지 않는다. 다만 아래 CX-12 · CX-22 의 주석 · 쿼리가 "공개 = status 'A'" 를 뒷받침한다. 뷰 정의는 별도 확인이 필요하다.

## B. 목록의 기본 정렬 · 제외 조건

### CX-12 정렬을 지정하지 않으면 난이도 내림차순, 같은 난이도는 번호(id) 오름차순이다
- 근거: `search.php:317-319`
- 코드:
  ```php
  case '':
      // 기본 정렬: 어려운 문항부터, 같은 난이도면 번호 순
      $orderBy = " ORDER BY level DESC, id ASC";
  ```

### CX-13 정렬 기준은 `id|title|unit|level|created` 만 허용하며, 그 밖의 값은 경고 후 기본 정렬(CX-12)로 돌린다
- 근거: `search.php:266`, `search.php:322-326`
- 코드:
  ```php
  $sort = strtolower(trim($sort));
  ...
  default:
      $warnings[] = '알 수 없는 정렬 기준입니다. 기본 정렬을 사용합니다.';
      $orderBy = " ORDER BY level DESC, id ASC";
  ```

### CX-14 정렬 방향은 `asc|desc` 만 유효하고, 그 밖의 값은 무시(경고)한다. 방향이 없을 때의 기본 방향은 기준마다 다르다
- 근거: `search.php:268-273`, `search.php:278-281`, `search.php:302-306`, `search.php:310-314`
- 코드:
  ```php
  if ($dir !== 'asc' && $dir !== 'desc') { ... $dir = ''; }
  case 'level':   if ($dir === 'asc') { ... level ASC, id ASC } else { ... level DESC, id ASC }
  case 'created': if ($dir === 'asc') { ... created_at ASC, id ASC } else { ... created_at DESC, id ASC }
  ```
- 비고: 방향이 없으면 `level` · `created` 는 내림차순, `id` · `title` · `unit` 은 오름차순이다(`search.php:281`, `289`, `297` 의 else 분기와 `search.php:330`).

### CX-15 `unit` 정렬은 방향과 관계없이 보조 키로 난이도 내림차순을 쓴다
- 근거: `search.php:293-298`
- 코드:
  ```php
  $orderBy = " ORDER BY unit_code DESC, level DESC, id ASC";
  ...
  $orderBy = " ORDER BY unit_code ASC, level DESC, id ASC";
  ```

### CX-16 `id` 정렬을 뺀 모든 정렬에서 동률은 id 오름차순으로 결정한다
- 근거: `search.php:287`, `search.php:289`, `search.php:295`, `search.php:297`, `search.php:303`, `search.php:305`, `search.php:311`, `search.php:313`
- 코드:
  ```php
  $orderBy = " ORDER BY title DESC, id ASC";
  $orderBy = " ORDER BY created_at ASC, id ASC";
  ```
- 비고: 정렬 방향(`desc`)이 id 보조 키에는 적용되지 않는다. `id` 정렬만 id 자체가 방향을 따른다(`search.php:279`).

### CX-17 한 페이지는 20건이며, 페이지 번호는 1~999 로 보정한다. 숫자가 아니면 1페이지를 쓴다
- 근거: `search.php:336-349`, `search.php:523`
- 코드:
  ```php
  if ($page < 1) { $page = 1; }
  if ($page > 999) { $page = 999;
  $offset = ($page - 1) * 20;
  $limit  = " LIMIT 20 OFFSET " . (int)$offset;
  ```
- 비고: 총 건수(`COUNT(*)`)는 페이지와 무관하게 같은 조건 전체에서 센다(`search.php:526`). `0` 은 숫자 정규식을 통과한 뒤 1 로 올려진다(`search.php:337-344`).

### CX-18 단원 목록의 "공개 문항 수"는 상태가 `A` 인 문항만 센다(삭제 · 검수중 제외)
- 근거: `units.php:15-17`
- 코드:
  ```php
  // 단원별 공개(status='A') 문항 수. 삭제 · 검수중 문항은 세지 않는다.
  (SELECT COUNT(*) FROM item i WHERE i.unit_id = u.id AND i.status = 'A') AS item_count
  ```
- 비고: 단원 목록은 학년 → 코드 오름차순(`units.php:19`). 검색 화면의 단원 선택 목록도 같은 순서다(`search.php:156`). 태그 선택 목록은 id 오름차순이다(`search.php:178`).

## C. 등록 시 검증 (register.php)

### CX-19 제목은 앞뒤 공백을 제거한 뒤 5자 이상이어야 한다
- 근거: `register.php:41`, `register.php:48-51`
- 코드:
  ```php
  $title  = isset($_POST['title'])  ? trim((string)$_POST['title'])  : '';
  if (mb_strlen($title, 'UTF-8') < 5) {
      $errors[] = '제목은 5자 이상 입력해야 합니다.';
  ```
- 비고: 내부 공백은 글자 수에 포함된다(`register.php:48` 주석 "공백 제외 아님").

### CX-20 제목은 200자를 넘을 수 없다
- 근거: `register.php:52-54`
- 코드:
  ```php
  if (mb_strlen($title, 'UTF-8') > 200) {
      $errors[] = '제목은 200자를 넘을 수 없습니다.';
  ```

### CX-21 지문은 필수이며, 앞뒤 공백 제거 후 빈 문자열이면 오류다
- 근거: `register.php:42`, `register.php:55-58`
- 코드:
  ```php
  $stem   = isset($_POST['stem'])   ? trim((string)$_POST['stem'])   : '';
  if ($stem === '') {
      $errors[] = '지문을 입력해야 합니다.';
  ```
- 비고: 지문 최대 길이 검증은 이 코드에 없다.

### CX-22 단원은 필수이며, 단원 테이블에 존재하는 id 여야 한다
- 근거: `register.php:59-68`
- 코드:
  ```php
  foreach ($units as $u) {
      if ((string)$u['id'] === $unitId) {
          $unitOk = true;
  ...
  $errors[] = '단원을 선택해야 합니다.';
  ```

### CX-23 난이도는 `1`~`5` 한 자리 숫자여야 한다
- 근거: `register.php:69-72`
- 코드:
  ```php
  if (!preg_match('/^[1-5]$/', $level)) {
      $errors[] = '난이도는 1~5 사이여야 합니다.';
  ```
- 비고: 검색(CX-08)과 달리 범위 밖 값은 저장 전에 오류로 막는다.

### CX-24 태그는 선택 사항이며, 태그 목록에 없는 id 는 오류 없이 조용히 버린다
- 근거: `register.php:73-81`
- 코드:
  ```php
  // 태그는 목록에 있는 것만
  if ((string)$t['id'] === (string)$tid) {
      $validTagIds[] = (int)$tid;
  ```
- 비고: 태그를 하나도 안 골라도 오류가 없다. 걸러진 태그는 검증 오류 목록(`$errors`)에 들어가지 않는다.

### CX-25 검증을 모두 통과한 경우에만 저장하며, 새 문항의 상태는 검수중 `R` 이다
- 근거: `register.php:83-94`
- 코드:
  ```php
  if (empty($errors)) {
      // 등록 직후에는 검수중(R) 상태로 들어간다 → 검수 완료 전까지 검색 화면에 나오지 않는다.
      "INSERT INTO item (id, unit_id, title, stem, level, status, created_at, updated_at)
       VALUES (?, ?, ?, ?, ?, 'R', NOW(), NOW())"
  ```
- 비고: `R` 상태는 CX-18 의 `status = 'A'` 조건에 걸리지 않는다. 검색 화면에서 안 나온다는 부분은 주석이며, 뷰(CX-11) 정의가 없어 이 폴더 코드로는 확인하지 못했다.

### CX-26 문항 번호는 `MAX(id)+1` 을 `FOR UPDATE` 로 구해 직접 매기고, 문항과 태그 저장은 한 트랜잭션이며 실패하면 롤백한다
- 근거: `register.php:85-90`, `register.php:114`, `register.php:119-122`
- 코드:
  ```php
  $conn->begin_transaction();
  $res = $conn->query("SELECT COALESCE(MAX(id), 0) + 1 AS next_id FROM item FOR UPDATE");
  ...
  } catch (Exception $e) {
      $conn->rollback();
  ```
- 비고: 실패 시 사용자에게는 `'저장 중 오류가 발생했습니다.'` 만 보인다(`register.php:122`).

## 범위 밖 · 확인하지 못한 것
- **수정 시의 검증**: `legacy/item-bank-php` 에 문항 수정 화면 · 코드가 없다(`index.php` `register.php` `search.php` `units.php` 만 존재). 수정 규칙은 뽑지 못했다.
- **`v_item_public` 뷰 정의**와 DB 스키마(콜레이션 · 제약)는 이 폴더에 없어 확인하지 못했다(CX-04, CX-11, CX-25 비고).
- `index.php` · `inc/layout.php` 는 읽지 않았다(검색 · 등록 · 정렬 규칙과 무관한 진입점 · 레이아웃으로 보고 범위에서 뺐다).
