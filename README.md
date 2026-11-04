# DeKUT First-Year Registration (CCS 3211 CAT 1)

Java · Android Studio · Firebase Realtime Database · M-Pesa Daraja STK Push

## 1. Firebase setup
1. Firebase console → create a project → **Add Android app** with package `com.dekut.register`.
2. Download `google-services.json` and put it in `app/`.
3. Build → **Realtime Database** → Create database → start in test mode (or paste `database.rules.json`).
   NOTE: these rules are open for the demo only – anyone could read/write. Tighten before real use.

## 2. M-Pesa backend (Cloud Functions)
```
npm i -g firebase-tools && firebase login && firebase use <project-id>
cd functions && npm install && cp .env.example .env     # fill in Daraja keys
firebase deploy --only functions                        # copy the mpesaCallback URL into .env
firebase deploy --only functions                        # deploy again so the callback URL is picked up
```
Cloud Functions need the Blaze (pay-as-you-go) plan. Any other host (Render, a Node server) also works – the
code is plain Node 20.

Then paste the **stkPush** URL into `app/src/main/java/com/dekut/register/Config.java`.

## 3. Run
Open the folder in Android Studio, sync Gradle, run on a phone/emulator.

## 4. Demo script (database marks)
1. Online: register a student → show the node appear in the Firebase console.
2. Airplane mode ON: register another student → banner turns red; console does NOT show it.
3. Airplane mode OFF: banner turns green → the node appears in the console by itself.

## Data model
```
students/{idNo}  fullNames, idNo, regNo, gender, course, department, status, receipt
books/{idNo}/{pushId}  isbn, author, title, dateBorrowed, dateReturn, studentId
hostels/{idNo}   location, roomType, sharable, studentId
payments/{idNo}  status (PENDING|SUCCESS|FAILED), receipt, amount, phone   <- written by the backend
checkouts/{CheckoutRequestID} = idNo                                         <- backend only
```
