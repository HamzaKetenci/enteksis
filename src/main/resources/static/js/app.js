/**
 * ENTEKSİS - TALEP FORMU YÖNETİMİ
 * 
 * Güvenlik ve Erişilebilirlik İlkeleri:
 * 1. innerHTML KULLANILMAZ - XSS'i önlemek için tüm metinler textContent ile basılır.
 * 2. İstemci kuralları sunucu kurallarıyla birebir aynıdır (RFC 5322 e-posta regexi dahil).
 * 3. Başarı durumu YALNIZCA HTTP 201 Created yanıtı geldiğinde gösterilir.
 * 4. Hata durumunda kullanıcının girdiği form verileri silinmez, ilk hatalı alana odaklanılır.
 * 5. Çift gönderimi önlemek için form gönderilirken buton devre dışı bırakılır.
 */

document.addEventListener('DOMContentLoaded', () => {
  const form = document.getElementById('request-form');
  const submitBtn = document.getElementById('submit-btn');
  const btnText = document.getElementById('btn-text');
  const btnSpinner = document.getElementById('btn-spinner');
  const alertBox = document.getElementById('form-alert');

  const fields = {
    name: {
      input: document.getElementById('name'),
      error: document.getElementById('name-error')
    },
    email: {
      input: document.getElementById('email'),
      error: document.getElementById('email-error')
    },
    service: {
      input: document.getElementById('service'),
      error: document.getElementById('service-error')
    },
    message: {
      input: document.getElementById('message'),
      error: document.getElementById('message-error')
    },
    contactValidation: {
      input: document.getElementById('contactValidation')
    }
  };

  // Sunucuyla birebir eşleşen ortak e-posta regexi
  const EMAIL_REGEX = /^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\.[a-zA-Z]{2,}$/;
  const VALID_SERVICES = ['gorev-otomasyonu', 'rapor-otomasyonu', 'entegrasyon', 'diger'];

  // Alan hatalarını temizleme
  function clearErrors() {
    Object.keys(fields).forEach((key) => {
      const field = fields[key];
      if (field.input) {
        field.input.removeAttribute('aria-invalid');
      }
      if (field.error) {
        field.error.textContent = '';
        field.error.hidden = true;
      }
    });
    alertBox.hidden = true;
    alertBox.textContent = '';
    alertBox.className = 'alert-box';
  }

  // Belirli bir alana hata basma
  function setFieldError(fieldKey, message) {
    const field = fields[fieldKey];
    if (field && field.input && field.error) {
      field.input.setAttribute('aria-invalid', 'true');
      field.error.textContent = message;
      field.error.hidden = false;
    }
  }

  // Genel bildirim kutusunu ayarlama
  function showAlert(message, type) {
    alertBox.textContent = message;
    alertBox.className = type === 'success' ? 'alert-box alert-success' : 'alert-box alert-error';
    alertBox.hidden = false;
  }

  // İstemci tarafı doğrulama (Client-side validation)
  function validateClientSide(data) {
    const errors = {};

    // İsim kontrolü
    if (!data.name || data.name.trim().length === 0) {
      errors.name = 'İsim alanı boş bırakılamaz.';
    } else if (data.name.trim().length < 2 || data.name.trim().length > 80) {
      errors.name = 'İsim 2 ile 80 karakter arasında olmalıdır.';
    }

    // E-posta kontrolü
    if (!data.email || data.email.trim().length === 0) {
      errors.email = 'E-posta alanı boş bırakılamaz.';
    } else if (data.email.trim().length > 254) {
      errors.email = 'E-posta en fazla 254 karakter olabilir.';
    } else if (!EMAIL_REGEX.test(data.email.trim())) {
      errors.email = 'Geçerli bir e-posta adresi giriniz (örn: ad@sirket.com).';
    }

    // Hizmet seçimi kontrolü
    if (!data.service || !VALID_SERVICES.includes(data.service)) {
      errors.service = 'Lütfen geçerli bir hizmet seçiniz.';
    }

    // Açıklama kontrolü
    if (!data.message || data.message.trim().length === 0) {
      errors.message = 'Açıklama alanı boş bırakılamaz.';
    } else if (data.message.trim().length < 10 || data.message.trim().length > 1000) {
      errors.message = 'Açıklama 10 ile 1000 karakter arasında olmalıdır.';
    }

    return errors;
  }

  // Form gönderme durumu yönetimi
  function setLoading(isLoading) {
    submitBtn.disabled = isLoading;
    if (isLoading) {
      btnText.textContent = 'Gönderiliyor...';
      btnSpinner.hidden = false;
    } else {
      btnText.textContent = 'Talebi Gönder';
      btnSpinner.hidden = true;
    }
  }

  form.addEventListener('submit', async (e) => {
    e.preventDefault();
    clearErrors();

    const payload = {
      name: fields.name.input.value,
      email: fields.email.input.value,
      service: fields.service.input.value,
      message: fields.message.input.value,
      contactValidation: fields.contactValidation.input ? fields.contactValidation.input.value : ''
    };

    // 1. İstemci Doğrulaması
    const clientErrors = validateClientSide(payload);
    const errorKeys = Object.keys(clientErrors);

    if (errorKeys.length > 0) {
      errorKeys.forEach((key) => {
        setFieldError(key, clientErrors[key]);
      });
      showAlert('Lütfen formdaki hatalı alanları düzeltiniz.', 'error');
      // İlk hatalı alana erişilebilir odaklanma
      fields[errorKeys[0]].input.focus();
      return;
    }

    // 2. Sunucuya Gönderim
    setLoading(true);

    try {
      const response = await fetch('/api/requests', {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          'Accept': 'application/json'
        },
        body: JSON.stringify(payload)
      });

      // HTTP 201 CREATED: YALNIZCA bu durumda başarı gösterilir
      if (response.status === 201) {
        const result = await response.json().catch(() => ({}));
        form.reset();
        showAlert(result.message || 'Talebiniz başarıyla alındı. En kısa sürede sizinle iletişime geçeceğiz.', 'success');
        alertBox.focus();
        return;
      }

      // HTTP 422 UNPROCESSABLE ENTITY: Doğrulama Hatası
      if (response.status === 422) {
        const errorData = await response.json().catch(() => ({}));
        if (errorData.errors && typeof errorData.errors === 'object') {
          const serverErrorKeys = Object.keys(errorData.errors);
          serverErrorKeys.forEach((key) => {
            setFieldError(key, errorData.errors[key]);
          });
          showAlert('Girdiğiniz bilgileri kontrol ediniz.', 'error');
          if (serverErrorKeys.length > 0 && fields[serverErrorKeys[0]]) {
            fields[serverErrorKeys[0]].input.focus();
          }
        } else if (errorData.error) {
          showAlert(errorData.error, 'error');
        } else {
          showAlert('Doğrulama hatası oluştu.', 'error');
        }
        return;
      }

      // HTTP 429 TOO MANY REQUESTS: Rate Limit
      if (response.status === 429) {
        const errorData = await response.json().catch(() => ({}));
        showAlert(errorData.error || 'Çok fazla istek gönderildi. Lütfen bir dakika bekleyiniz.', 'error');
        return;
      }

      // HTTP 413 PAYLOAD TOO LARGE
      if (response.status === 413) {
        showAlert('Gönderilen veri boyutu izin verilen sınırı aşıyor.', 'error');
        return;
      }

      // HTTP 400 BAD REQUEST
      if (response.status === 400) {
        showAlert('Geçersiz istek formatı iletildi.', 'error');
        return;
      }

      // HTTP 500 / 503 / Diğer Hatalar
      const errorData = await response.json().catch(() => ({}));
      showAlert(errorData.error || 'Sunucu ile iletişim kurulamadı. Lütfen daha sonra tekrar deneyiniz.', 'error');

    } catch (err) {
      // Ağ hatası veya bağlantı kesintisi
      showAlert('Ağ bağlantısı kurulamadı. Lütfen internet bağlantınızı kontrol edip tekrar deneyiniz.', 'error');
    } finally {
      setLoading(false);
    }
  });
});
