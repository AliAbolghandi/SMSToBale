// توکن ربات بله (از باتفادر بله دریافت می‌شود)
const BALE_BOT_TOKEN = "PASTE_YOUR_BALE_BOT_TOKEN_HERE";

// کلید محرمانه دلخواه - باید دقیقا در اپ اندروید هم همین مقدار وارد شود
const SECRET_KEY = "PASTE_A_RANDOM_SECRET_HERE";

function doPost(e) {
  try {
    const data = JSON.parse(e.postData.contents);

    if (data.secret !== SECRET_KEY) {
      return jsonResponse({ status: "error", message: "unauthorized" });
    }

    const chatId = data.chatId;
    const message = data.message;

    if (!chatId || !message) {
      return jsonResponse({ status: "error", message: "chatId or message missing" });
    }

    const result = sendBaleMessage(chatId, message);
    return jsonResponse({ status: "ok", result: result });

  } catch (err) {
    return jsonResponse({ status: "error", message: err.toString() });
  }
}

function sendBaleMessage(chatId, text) {
  const url = "https://tapi.bale.ai/bot" + BALE_BOT_TOKEN + "/sendMessage";
  const payload = {
    chat_id: chatId,
    text: text
  };
  const options = {
    method: "post",
    contentType: "application/json",
    payload: JSON.stringify(payload),
    muteHttpExceptions: true
  };
  const response = UrlFetchApp.fetch(url, options);
  return response.getContentText();
}

function jsonResponse(obj) {
  return ContentService
    .createTextOutput(JSON.stringify(obj))
    .setMimeType(ContentService.MimeType.JSON);
}

// جداگانه از ویرایشگر اسکریپت اجرا کن تا chat_id مخاطبی که /start زده را در Logger ببینی
function getChatIdHelper() {
  const url = "https://tapi.bale.ai/bot" + BALE_BOT_TOKEN + "/getUpdates";
  const response = UrlFetchApp.fetch(url);
  Logger.log(response.getContentText());
}
