# 沟通、AI 与图片上传接口

版本：V1 设计，2026-11-20。尚未启动 HTTP 服务或调用收费模型。公共规则见 [接口文档](接口文档.md)，完整字段见 [接口字段说明](接口字段说明.md)，源定义见 [沟通与AI.json](分模块契约/沟通与AI.json)。

## 接口索引

| 方法与路径 | 用途 | 权限 |
| --- | --- | --- |
| `POST /api/v1/me/consultations` | 发起咨询并发送首条消息 | MEMBER |
| `GET /api/v1/me/consultations` | 查询我的咨询会话 | MEMBER |
| `GET /api/v1/me/consultations/{threadId}/messages` | 读取会话消息 | MEMBER |
| `POST /api/v1/me/consultations/{threadId}/messages` | 发送会话文字消息 | MEMBER |
| `PUT /api/v1/me/consultations/{threadId}/read-position` | 更新会话已读位置 | MEMBER |
| `GET /api/v1/farmer/consultations` | 查询我的咨询会话 | FARMER |
| `GET /api/v1/farmer/consultations/{threadId}/messages` | 读取会话消息 | FARMER |
| `POST /api/v1/farmer/consultations/{threadId}/messages` | 发送会话文字消息 | FARMER |
| `PUT /api/v1/farmer/consultations/{threadId}/read-position` | 更新会话已读位置 | FARMER |
| `GET /api/v1/ai/capabilities` | 查看AI入口与可用性 | PUBLIC |
| `POST /api/v1/me/ai/conversations` | 建立AI导购会话 | MEMBER |
| `GET /api/v1/me/ai/conversations` | 查询本人AI会话 | MEMBER |
| `GET /api/v1/me/ai/conversations/{conversationId}/messages` | 读取AI会话历史消息 | MEMBER |
| `POST /api/v1/me/ai/conversations/{conversationId}/requests` | 提交一次导购提问 | MEMBER |
| `GET /api/v1/me/ai/conversations/{conversationId}/requests/{requestId}` | 查询一次导购请求进度 | MEMBER |
| `POST /api/v1/uploads/images` | 上传业务图片 | MEMBER |

## 异步留言流程

用户从农户、商品或有效推广详情发起第一条文字消息；服务端按咨询用户＋农户归并会话，发送事务分配会话内 seq，后续按双方各自入口读取／回复。农户自身账号仍可以作为其他农户的买家，不能按“账号有农户身份”把所有消息都当成经营回复。本轮不提供向自己的经营身份咨询的操作。

同会话同发送者的 Idempotency-Key 对应 client_message_id；重复发送返回原消息，同键不同内容或来源409。调用者不能传 sender_user_id 或替对方更新 read_seq。已读只向前推进且不超过 last_seq，未读统计对方发来且超过本人位置的条数。列表与消息读取本身不自动把所有消息标为已读。

初次进入消息页不传游标取得最近20条，返回seq升序；向上翻页传 beforeSeq，获取新消息传 afterSeq，两者不能一起传。页面可见时可每5秒读取新消息，进入后台停止；已读位置使用明确的 PUT 操作更新。这是本轮简单轮询实现，不依赖 WebSocket 或双方同时在线。

SOURCE=PRODUCT需要自家目标商品；CAMPAIGN需专题和商品的有效来源；ASSISTANCE需当前可用助农信息和其商品；FARMER为农户主页；DIRECT只用于已有会话。发送时重新检查来源，不能从已结束／暂隐藏助农信息伪造新咨询。正常既有会话的DIRECT续聊不再反复记推广咨询事件；留言不产生订单、预约或库存占用。

## AI 接受请求与查询结果

采用普通 JSON 的异步接受＋轮询方案，以同一实现适配微信小程序和 H5。POST /requests 成功返回202以及 userMessage、assistantMessage 和建议 pollAfterMs；前端按原 requestId 调用 GET /requests/{requestId}，取得 COMPLETED / FAILED / REJECTED 后停止，后台页面暂停轮询。不把网络断开理解成退款或停止上游计费。

消息存储：用户文本为USER/COMPLETED，导购占位为ASSISTANT/PENDING；同一业务键最多一对展示消息，同会话同时只允许一个正在生成的回答。后台执行权必须在持久化占位成功后领取，不能让两个HTTP重试各启动一次模型；数据库事务中不等待外部API返回。重试查询已存在的结果，不增加内部call_no。

执行与恢复：后端提交消息事务后向受控执行器投递任务；投递失败将原回复标为FAILED并保留同请求结果，不能返回接受后永远PENDING。进程重启时扫描未完成请求：确认尚未发送上游的可按原执行权恢复；无法确定是否已调用的标为失败／待核对费用，不盲目重复收费调用。每次真正内部调用单独记录ai_usage_log、预留预算和usage，未知不能补0。执行器、超时与预算锁在工程实现时验证。202 接受后的预算不足、执行或供应商失败均写回原 ASSISTANT 消息的 FAILED 与 errorCode，轮询以 HTTP 200 返回终态；接受前拒绝才使用对应的 HTTP 503／429，不能改写已发送的 202。

输入仅content，最多1000字符，拒绝model/baseUrl/apiKey/systemPrompt/tools等额外字段。这个字符上限是表单设计，不代替token、上下文、内部调用数和总预算配置；未完成价格／预算配置时返回AI_NOT_CONFIGURED，不用示例额度收费。首版没有个人每日固定次数。

回答文本最长16000字符，商品卡片最多6张，均为本轮响应容量设计；模型输入输出token限制仍按部署的费用配置执行。后端从业务查询结果核验商品卡片，历史读取也重新检查当前可购买状态。无结果时可正常完成并返回空products及原因；无关任务返回固定REJECTED提示，超时／供应商失败返回原请求FAILED并提示搜索入口；不把失败自动变为一个新收费请求。

模型供应商、实际价格、具体频率/预算、聊天保留与删除策略仍待AI模块配置确认，不开放通用代理、任意SQL工具、管理员查看全部私聊或用户随意选择模型的接口。

## 统一图片上传

使用 POST /api/v1/uploads/images，multipart 的 file 和 purpose 必填；单文件5MiB以内，支持实际校验通过的JPEG/PNG/WebP。不同purpose复用同一上传接口并校验相应经营／管理员身份；资源由后端命名并保存到持久存储，返回key和预览URL。

保存商品、图文、头像或评价时重新校验资源是否属于允许的账号和用途。图片URL可公开，不表示任意人可写；不接受远程URL作为服务器代下载参数。没有独立文件数据库表，首版资源目录按账号与用途隔离；文件清理不得删除仍被商品、历史订单或评价引用的资源。上传限制为常规实现参数，不增加入驻证件或强制评价图片。

## 每项接口的操作规则

### 发起咨询并发送首条消息

`POST /api/v1/me/consultations` · `startConsultation`

仅登录用户。锁定双方唯一会话，不存在则创建，同时写首条消息；FARMER主页来源可不选商品，其余按来源校验。初次不允许DIRECT，禁止自己咨询自己。Idempotency-Key映射消息client_message_id（同会话同发送者唯一），既有成功重试返回原消息；新来源失效不抹去既有成功。消息和推广CONSULT事件同事务提交，不创建订单或占用库存。

主要错误：409 `SOURCE_UNAVAILABLE`：原商品或推广来源不可用于发起咨询；400 `SELF_CONSULTATION`：不能向自己的农户身份发起咨询；409 `IDEMPOTENCY_CONFLICT`：同一发送键对应不同内容或来源。

需求：FR-COMM-001、FR-COMM-002；验收：AC-COMM-001、AC-COMM-003。

### 查询我的咨询会话

`GET /api/v1/me/consultations` · `buyerConsultations`

只返回当前账号在本侧的会话；农户账号的买家会话与经营会话分别查询。按last_message_at降序、id降序。未读只统计对方发送且seq大于本侧读取位置的消息。管理员不因其身份获得此接口的他人聊天权限。

需求：FR-COMM-001；验收：AC-COMM-001。

### 读取会话消息

`GET /api/v1/me/consultations/{threadId}/messages` · `buyerConsultationMessages`

校验当前侧归属。beforeSeq与afterSeq最多传一个；不传取最近limit条并按seq升序返回，向前翻页用beforeSeq，页面可见时获取新消息用afterSeq。limit默认20最大100；读取本身不改变已读位置。游标指本会话消息序号，不按全局ID或时间分页。

需求：FR-COMM-001；验收：AC-COMM-001。

### 发送会话文字消息

`POST /api/v1/me/consultations/{threadId}/messages` · `buyerSendConsultationMessage`

校验双方会话归属；本侧身份由路由和登录账号确定，客户端不能指定senderUserId。农户回复只能DIRECT，买家可以DIRECT或经过校验的新来源。锁会话分配seq、写消息及来源统计；同Idempotency-Key映射client_message_id，重试不多发、不多计咨询。新消息成功后未读位置不自动推进到对方后来消息。禁用后的存量通信权限仍须按待确认规则配置，不能默认放开。

主要错误：409 `SOURCE_UNAVAILABLE`：原商品或推广来源不可用于发起咨询；400 `SELF_CONSULTATION`：不能向自己的农户身份发起咨询；409 `IDEMPOTENCY_CONFLICT`：同一发送键对应不同内容或来源。

需求：FR-COMM-001、FR-COMM-002；验收：AC-COMM-003。

### 更新会话已读位置

`PUT /api/v1/me/consultations/{threadId}/read-position` · `buyerMarkConsultationRead`

本人侧read_seq只向前推进；重复或更小值保持原位置，超过last_seq拒绝。消息读取与确认已读分开；更新不能替另一侧标为已读。

主要错误：400 `READ_POSITION_INVALID`：已读位置超出本会话已提交消息。

需求：FR-COMM-001；验收：AC-COMM-001。

### 查询我的咨询会话

`GET /api/v1/farmer/consultations` · `farmerConsultations`

只返回当前账号在本侧的会话；农户账号的买家会话与经营会话分别查询。按last_message_at降序、id降序。未读只统计对方发送且seq大于本侧读取位置的消息。管理员不因其身份获得此接口的他人聊天权限。

需求：FR-COMM-001；验收：AC-COMM-001。

### 读取会话消息

`GET /api/v1/farmer/consultations/{threadId}/messages` · `farmerConsultationMessages`

校验当前侧归属。beforeSeq与afterSeq最多传一个；不传取最近limit条并按seq升序返回，向前翻页用beforeSeq，页面可见时获取新消息用afterSeq。limit默认20最大100；读取本身不改变已读位置。游标指本会话消息序号，不按全局ID或时间分页。

需求：FR-COMM-001；验收：AC-COMM-001。

### 发送会话文字消息

`POST /api/v1/farmer/consultations/{threadId}/messages` · `farmerSendConsultationMessage`

校验双方会话归属；本侧身份由路由和登录账号确定，客户端不能指定senderUserId。农户回复只能DIRECT，买家可以DIRECT或经过校验的新来源。锁会话分配seq、写消息及来源统计；同Idempotency-Key映射client_message_id，重试不多发、不多计咨询。新消息成功后未读位置不自动推进到对方后来消息。禁用后的存量通信权限仍须按待确认规则配置，不能默认放开。

主要错误：409 `SOURCE_UNAVAILABLE`：原商品或推广来源不可用于发起咨询；400 `SELF_CONSULTATION`：不能向自己的农户身份发起咨询；409 `IDEMPOTENCY_CONFLICT`：同一发送键对应不同内容或来源。

需求：FR-COMM-001、FR-COMM-002；验收：AC-COMM-003。

### 更新会话已读位置

`PUT /api/v1/farmer/consultations/{threadId}/read-position` · `farmerMarkConsultationRead`

本人侧read_seq只向前推进；重复或更小值保持原位置，超过last_seq拒绝。消息读取与确认已读分开；更新不能替另一侧标为已读。

主要错误：400 `READ_POSITION_INVALID`：已读位置超出本会话已提交消息。

需求：FR-COMM-001；验收：AC-COMM-001。

### 查看AI入口与可用性

`GET /api/v1/ai/capabilities` · `getAiCapabilities`

只返回静态入口能力和公开可用性，不调用任何收费模型，不暴露预算余额、供应商Key或内部系统提示词。个人每日次数不作为限制字段。

需求：FR-AI-006、FR-AI-007；验收：AC-AI-005、AC-AI-007。

### 建立AI导购会话

`POST /api/v1/me/ai/conversations` · `createAiConversation`

创建空会话，不调用收费模型；由当前账号拥有。标题选填，默认在首次合法用户消息后取前30字符生成，不调用模型生成标题。创建失败先查列表，不能声称会话创建具有跨崩溃幂等保障。

需求：FR-AI-002、FR-AI-007；验收：AC-AI-008。

### 查询本人AI会话

`GET /api/v1/me/ai/conversations` · `listAiConversations`

仅本人会话，按lastActivityAt和id降序。可查询仍保留的数据；不承诺永久历史，保留/删除策略仍待AI模块落实。

需求：FR-AI-002；验收：AC-AI-008。

### 读取AI会话历史消息

`GET /api/v1/me/ai/conversations/{conversationId}/messages` · `listAiMessages`

校验会话所有者；不传beforeSeq取最近limit条，返回按seq升序。历史卡片必须重新核验商品，失效/缺货/下架商品不再作为可购买推荐返回；原文字保留但不能据其现价下单。只读查询不调用模型。

需求：FR-AI-002、FR-AI-003；验收：AC-AI-003、AC-AI-008。

### 提交一次导购提问

`POST /api/v1/me/ai/conversations/{conversationId}/requests` · `submitAiRequest`

先验证登录、归属、会话ACTIVE、输入长度和业务参数白名单，再查同Idempotency-Key已有消息；同键同内容返回原进度，异内容409。同会话同时最多一个PENDING回复；处理中新的提问409，不重复发起收费调用。接受后持久化USER COMPLETED和ASSISTANT PENDING，释放数据库事务后由受控后台执行导购，返回202供轮询。必要费用预留在每次上游调用前完成。接受前发现缺少供应商/预算配置或额度不足返回503，短时限流429；已返回202后发生预算争抢、执行失败或上游异常时，将原ASSISTANT消息写为FAILED并带errorCode，GET原请求返回200及该终态，不尝试改写已经返回的HTTP状态，也不无限保留PENDING。请求体仅content，不允许model/baseUrl/tools/systemPrompt/temperature等通用模型代理参数。确定的本地无关任务拒绝可生成REJECTED固定提示，不收费；需要收费的判断也受调用预算限制。

主要错误：503 `AI_NOT_CONFIGURED`：模型或费用配置尚未就绪；503 `AI_BUDGET_EXHAUSTED`：平台AI预算暂不足，请使用搜索选购；503 `AI_UNAVAILABLE`：AI依赖暂不可用；409 `AI_BUSY`：本会话仍有回答生成中；429 `RATE_LIMITED`：请求过于频繁；409 `IDEMPOTENCY_CONFLICT`：同一请求键对应不同问题；409 `AI_CONVERSATION_CLOSED`：该会话不可继续。

需求：FR-AI-001、FR-AI-002、FR-AI-003、FR-AI-004、FR-AI-005、FR-AI-006、FR-AI-007；验收：AC-AI-001、AC-AI-002、AC-AI-005、AC-AI-007、AC-AI-008。

### 查询一次导购请求进度

`GET /api/v1/me/ai/conversations/{conversationId}/requests/{requestId}` · `getAiRequest`

校验会话与请求归属；返回原两条消息最新状态。PENDING时按pollAfterMs轮询，终态停止；页面后台暂停轮询，回到前台继续读取。失败不自动另起模型请求；用户明确重试才产生新请求键，仍受频率/预算限制。业务模型超时标为FAILED或对应固定提示，用量未知保留待核对占用。接口不返回隐藏推理、工具原始结果或上游密钥。

需求：FR-AI-002、FR-AI-004；验收：AC-AI-002、AC-AI-008。

### 上传业务图片

`POST /api/v1/uploads/images` · `uploadImage`

multipart/form-data，file与purpose必填；单文件不超过5MiB，后端按实际内容校验JPEG/PNG/WebP并剥离元数据。禁止SVG/HTML/可执行内容；生成含账号归属的随机key，规范路径禁止..及目录穿越，返回key供表单保存。purpose=PRODUCT/FARMER_COVER/STORY须经营身份有效，ORIGIN/CAMPAIGN须管理员，REVIEW/AVATAR须本人账号。写业务资料时再次验证key所属账号及允许用途；访问URL可公开，上传元数据/私密业务归属不直接公开。客户端不能指定存储路径或外部URL；该接口不修改业务资料，不会自动创建商品。未引用资源清理策略在存储实现中确定。

主要错误：413 `UPLOAD_TOO_LARGE`：图片超过5MiB；415 `UPLOAD_TYPE_UNSUPPORTED`：只支持JPEG、PNG或WebP图片；403 `UPLOAD_PURPOSE_FORBIDDEN`：当前账号不能为该用途上传；503 `UPLOAD_UNAVAILABLE`：文件存储暂不可用。

需求：FR-PRODUCT-003、FR-FARM-003、FR-REVIEW-002、FR-CONTENT-001；验收：AC-FARM-003、AC-REVIEW-009、AC-REVIEW-012。
