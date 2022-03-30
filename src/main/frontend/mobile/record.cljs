(ns frontend.mobile.record
  (:require ["@capacitor/filesystem" :refer [Filesystem]]
            ["capacitor-voice-recorder" :refer [VoiceRecorder]]
            [promesa.core :as p]
            [frontend.handler.editor :as editor-handler]
            ["path" :as path]
            [frontend.state :as state]
            [frontend.mobile.util :as mobile-util]
            [frontend.date :as date]
            [lambdaisland.glogi :as log]
            [frontend.util :as util]))

(defn request-audio-recording-permission []
  (p/then
   (.requestAudioRecordingPermission VoiceRecorder)
   (fn [^js result] (.-value result))))

(defn- has-audio-recording-permission? []
  (p/then
   (.hasAudioRecordingPermission VoiceRecorder)
   (fn [^js result] (.-value result))))

(defn- set-recording-state []
  (p/catch
   (p/then (.getCurrentStatus VoiceRecorder)
           (fn [^js result]
             (let [{:keys [status]}
                   (js->clj result :keywordize-keys true)]
               (state/set-state! :editor/record-status status))))
   (fn [error]
     (js/console.error error))))

(defn start-recording []
  (p/let [permission-granted? (has-audio-recording-permission?)
          permission-granted? (or permission-granted?
                                  (request-audio-recording-permission))]
    (when permission-granted?
      (p/catch
       (p/then (.startRecording VoiceRecorder)
               (fn [^js _result]
                 (set-recording-state)
                 (js/console.log "Start recording...")))
       (fn [error]
         (log/error :start-recording-error error))))))

(defn get-asset-path [filename]
  (p/let [[repo-dir assets-dir]
          (editor-handler/ensure-assets-dir! (state/get-current-repo))
          path (path/join repo-dir assets-dir filename)]
    (if (mobile-util/native-android?)
      path
      (js/encodeURI (js/decodeURI path)))))

(defn- embed-audio [database64]
  (p/let [filename (str (date/get-date-time-string-2) ".mp3")
          edit-block (state/get-edit-block)
          format (:block/format edit-block)
          path (get-asset-path filename)
          _file (p/catch
                 (.writeFile Filesystem (clj->js {:data database64
                                                  :path path
                                                  :recursive true}))
                 (fn [error]
                   (log/error :file/write-failed {:path path
                                                  :error error})))
          url (util/format "../assets/%s" filename)
          file-link (editor-handler/get-asset-file-link format url filename true)]
    (when edit-block
      (state/append-current-edit-content! file-link))))

(defn stop-recording []
  (p/catch
   (p/then
    (.stopRecording VoiceRecorder)
    (fn [^js result]
      (let [value (.-value result)
            {:keys [_msDuration recordDataBase64 _mimeType]}
            (js->clj value :keywordize-keys true)]
        (set-recording-state)
        (when (string? recordDataBase64)
          (embed-audio recordDataBase64)
          (js/console.log "Stop recording...")
          ))))
   (fn [error]
     (js/console.error error))))

(defn pause-recording []
  (p/catch
   (p/then (.pauseRecording VoiceRecorder)
           (fn [^js _result]
             (set-recording-state)
             (js/console.log "Pause recording...")))
   (fn [error]
     (js/console.error error))))

(defn resume-recording []
  (p/catch
   (p/then (.resumeRecording VoiceRecorder)
           (fn [^js _result]
             (set-recording-state)
             (js/console.log "Resume recording...")))
   (fn [error]
     (js/console.error error))))

