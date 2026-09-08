# WorkPilot Mini — সেটআপ গাইড

এই অ্যাপে যোগ করা হয়েছে:
- **Attendance / Check-in** — দিনে একবার GPS লোকেশনসহ check-in
- **Visit Entry (Lead Generation)** — GPS auto-location সহ ভিজিট এন্ট্রি
- **Next Visit Reminder** — visit entry দেওয়ার সময় future date বেছে নিলে সেই তারিখে local push notification আসবে
- **Team system** — একজন team তৈরি করলে সে Admin হয় (team size 3–20, creation-এর সময় slider দিয়ে ঠিক করা যায়)। বাকিরা টিমে দুইভাবে ঢুকতে পারে:
  - নিজে থেকে: Admin-এর 6-digit invite code দিয়ে join করে (joiner = Member)
  - Admin সরাসরি: Dashboard → টিম কার্ড → "ইমেইল দিয়ে সরাসরি Member যোগ করুন" থেকে, যাকে যোগ করতে চান তার ইমেইল দিয়ে — শর্ত হলো তাকে আগে অ্যাপে Sign up করে থাকতে হবে এবং সে এখনো অন্য কোনো টিমে থাকা যাবে না
- **Multi-device sync** — Firebase Auth (Email/Password) + Firestore দিয়ে করা, তাই Admin আর member আলাদা ফোনেও একই টিম দেখবে

## ১. Firebase প্রজেক্ট বানান (একবারই লাগবে)

1. https://console.firebase.google.com এ গিয়ে নতুন প্রজেক্ট বানান
2. **Build → Authentication → Sign-in method** থেকে **Email/Password** enable করুন
3. **Build → Firestore Database** থেকে একটা Firestore database তৈরি করুন (production mode)
4. প্রজেক্টে একটা Android app যোগ করুন, package name দিন: `com.example.workpilotmini`
5. ডাউনলোড হওয়া `google-services.json` ফাইলটা এই প্রজেক্টের `app/` ফোল্ডারে রাখুন
   (path: `WorkPilotMini/app/google-services.json`) — এই ফাইল ছাড়া build হবে না।

## ২. Firestore Security Rules

`firestore.rules` ফাইলটা এই প্রজেক্টের রুটে দেওয়া আছে। Firebase Console → Firestore → Rules ট্যাবে গিয়ে এটার কন্টেন্ট পেস্ট করে Publish করুন।

## ৩. Firestore Index

Reminder লিস্ট (`uid` দিয়ে filter + `nextVisitDate` দিয়ে filter/sort একসাথে, member-দের জন্য) চালানোর জন্য একটা composite index লাগবে — এটা ছাড়া reminders স্ক্রিনে কোনো তারিখ/লিস্ট একদমই দেখাবে না (query silently fail করে, কোনো error UI-তে না দেখিয়েই)।

`firestore.indexes.json` ফাইলে এই index-টা ইতিমধ্যে define করা আছে। দুইভাবে বানানো যাবে:

- **Firebase CLI দিয়ে (recommended):** `npm i -g firebase-tools` → `firebase login` → প্রজেক্ট রুটে `firebase deploy --only firestore:indexes,firestore:rules` — এতে index আর rules দুটোই একসাথে deploy হয়ে যাবে।
- **অথবা ম্যানুয়ালি:** প্রথমবার অ্যাপ চালিয়ে reminders স্ক্রিনে গেলে Logcat-এ Firestore একটা error দেবে যেখানে একটা লিঙ্ক থাকবে — সেই লিঙ্কে ক্লিক করলেই ইনডেক্স অটো তৈরি হয়ে যাবে (কয়েক মিনিট সময় লাগে বানাতে, ততক্ষণ reminders খালি দেখাবে)।

## ৪. Build করা

```
./gradlew assembleDebug
```

Android Studio দিয়ে খুলে সরাসরি Run করাই সবচেয়ে সহজ।

## অ্যাপের ফ্লো

1. Sign up / Login (Firebase Auth)
2. প্রথমবার লগইনের পর: **টিম তৈরি করুন** (নাম + team size 3–20, স্লাইডার দিয়ে) → creator = Admin
   অথবা **টিমে join করুন** (Admin-এর কাছ থেকে পাওয়া 6-digit invite code দিয়ে) → joiner = Member
3. Dashboard থেকে:
   - **Attendance** — GPS সহ check-in, আজকে টিমে কারা check-in করেছে তার লিস্ট
   - **Visit Entry** — লিড নাম, নোট, GPS auto-location, ঐচ্ছিক "পরবর্তী ভিজিট" তারিখ
   - **Reminders** — যেসব visit-এ future তারিখ সেট করা হয়েছিল তাদের লিস্ট + সেই তারিখে ডিভাইসে local notification

## Admin vs Member visibility (এই আপডেটে যোগ হয়েছে)

- **Attendance:** Admin আজকে টিমে কে কে check-in করেছে তার পুরো লিস্ট দেখে। একজন সাধারণ Member শুধু নিজের check-in status দেখে, বাকিদের দেখে না।
- **Reminders:** Admin টিমের সবার upcoming reminder দেখে (কার নাম, কার কবে) — Member শুধু নিজের reminder-গুলো দেখে।
- এটা শুধু UI-তে না, Firestore rules-এও enforce করা (`firestore.rules`-এ `attendance`/`visits` read rule) — তাই client কোড বাইপাস করেও কেউ অন্যের ডেটা টানতে পারবে না।
- **⚠️ এই আপডেটের rules নতুন করে Publish করতে হবে** (উপরে ৩ নং ধাপ দেখুন), নাহলে পুরনো rules-ই চালু থাকবে এবং email-invite / visibility fix কোনোটাই কাজ করবে না।

## যা এখনো নেই (চাইলে পরে যোগ করা যাবে)

- টিম থেকে মেম্বার remove করার অপশন (এখন শুধু add করা যায়)
- Admin-এর জন্য পুরো টিমের visit (lead) history আলাদা করে ফিল্টার/এক্সপোর্ট — ডেটা মডেলে আছে, UI স্ক্রিন এখনো নেই
- Bangla/English ভাষা টগল (পরবর্তী ধাপে যোগ হবে)

## Security ও Error Handling (এই আপডেটে যোগ হয়েছে)

- **`firestore.rules` কঠোর করা হয়েছে** — আগে যেকোনো signed-in user যেকোনো টিমের ডেটা বদলাতে
  পারত (নিজেকে admin বানানো, অন্যের টিম hijack করা সহ)। এখন:
  - একজন ইউজার শুধু নিজের প্রোফাইল বদলাতে পারবে, আর role/teamId তখনই সেট হবে যখন সে
    আসলেই সেই টিমের admin (নিজে তৈরি করেছে) বা member (join code দিয়ে ঢুকেছে)।
  - টিমের বাকি ফিল্ড (নাম, invite code, adminUid) শুধু admin বদলাতে পারবে। নতুন member শুধু
    নিজেকে `memberUids`-এ যোগ করতে পারবে, অন্য কিছু না — আর টিম ফুল থাকলে সেটাও আটকানো।
  - Attendance রেকর্ড ইমিউটেবল (তৈরির পর edit/delete করা যাবে না)।
  - **⚠️ Firebase Console → Firestore → Rules ট্যাবে গিয়ে নতুন `firestore.rules` ফাইলের
    কন্টেন্ট পেস্ট করে Publish করতে হবে — নাহলে পুরনো ঢিলা rules-ই চালু থাকবে।**
- **User-facing error message আগে Firebase-এর raw English error দেখাত** (যেমন
  `FirebaseAuthInvalidCredentialsException...`)। এখন `AppError.kt`-এ centralized mapping
  আছে যা network / wrong-password / email-already-in-use / too-many-requests ইত্যাদির
  জন্য বোধগম্য বাংলা মেসেজ দেখায়।
- **Forgot password** — লগইন স্ক্রিনে "পাসওয়ার্ড ভুলে গেছেন?" যোগ হয়েছে, যেটা Firebase-এর
  reset ইমেইল পাঠায় (unregistered ইমেইলের ক্ষেত্রেও একই সাকসেস মেসেজ দেখায়, যাতে কেউ
  guess করে বের করতে না পারে কোন ইমেইলে অ্যাকাউন্ট আছে)।
- **Email format validation** signup/login/reset — সব জায়গায় যোগ হয়েছে।

### এরপর আরও শক্ত করতে চাইলে (optional, বড় স্কেলে গেলে সুপারিশ করি)
- **Firebase App Check** enable করা (Play Integrity provider) — bot/script দিয়ে API abuse
  আটকাতে। Console → App Check থেকে enable করে `com.google.firebase:firebase-appcheck-playintegrity`
  dependency যোগ করতে হবে।
- Cloud Functions দিয়ে team create/join সরানো, যাতে ক্লায়েন্ট সরাসরি Firestore-এ role লিখতে না পারে
  (এখনকার rules যথেষ্ট শক্ত, কিন্তু Cloud Functions সবচেয়ে নিরাপদ পন্থা)।
