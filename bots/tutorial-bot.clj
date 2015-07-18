(let [logged-in? (-> *login*
                     :status (= "ok"))

      ;;Health data
      hp (:health *hero-base*)
      max-hp (:max_health *hero-base*)
      alive? (:alive *hero-base*)
      hpp (if (integer? max-hp)
            (/ hp max-hp)
            1)

      ;;Energy data
      current-energy (get *hero-energy* :value 0)
      max-energy (get *hero-energy* :max 12)
      bonus-energy (get *hero-energy* :bonus 0)

      ;;Current Hero action information
      action-type (:type *hero-action*)
      action-percents (:percents *hero-action*)
      action-boss? (:is_boss *hero-action*)
      action-kind (cond
                    (contains? #{0 4 5 6 7 8 10 15} action-type) :safe
                    (= action-type 3) :fight
                    (contains? #{1 2 3 9} action-type) :danger
                    :else :default)

      ;;Information about help action and next cards available
      help-action-cost (get-in *api-info*
                           [:data :abilities_cost :help] 4)

      card-help-count (get *hero-cards* :help_count 0)
      card-help-barrier (get *hero-cards* :help_barrier 20)
      get-card? (>= card-help-count card-help-barrier)

      ;;Information about cards
      cards (:cards *hero-cards*)
      merge-cards (->> cards
                       (filter #(< (:rarity %) 3))
                       (group-by :rarity)
                       vals
                       (filter #(>= (count %) 3))
                       (map (fn [same-rarity-cards]
                              (mapv :uid same-rarity-cards)))
                       (#(when (not (empty? %)) %))
                       first)

      ;;Your bot command templates
      login-command [:login *email* *passwd*]
      info-command [:game-info]
      api-info-command [:api-info]
      help-command [:use-ability :help]
      get-card-command [:get-card]
      merge-cards-command [:combine-cards] ;don't forget to add the cards

      ;;Bot timing commands
      base-wait-time (int (+ (* 275 hpp hpp) (* 85 hpp)))
      safe-wait-time 360
      boss-wait-time (int (/ base-wait-time 2))
      wait-time (if action-boss? boss-wait-time base-wait-time)
      command-wait-time 5
      error-wait-time 30

      ;;Bot help values
      hpp-help-default 0.1
      fight-percent-to-help 0.8
      hpp-help-critical 0.05
      safe-pause-percent 0.5
      enough-energy? (>= current-energy  help-action-cost)

      ;;Remove lots of useless strings from 
      printable-hero-info (dissoc *hero-info*
                                  :diary :bag :messages :equipment)
      printable-session (if printable-hero-info
                          (assoc-in (dissoc *session* :response)
                                  [:body :game-info :data :account :hero]
                                  printable-hero-info)
                          *session*)
      ]
  (println "")
  (println printable-session)
  (println "Hero base info: " *hero-base*)
  (println "Energy: " *hero-energy*)
  (println "Action: " *hero-action* "(" action-kind ")")
  (println "Cards: " card-help-count "/" card-help-barrier)

  (cond
    (not *login*) (do (print "Logging in...")
                        [login-command api-info-command])

    (not *logged-in?*) (do (println "Couldn't log in...")
                           (print "Logging again in 30 seconds...")
                           [error-wait-time login-command])

    (not *game-info*) (do (println "Requesting game info first time...")
                          [info-command])

    (not *is-own?*) (do (println "Game info is NOT about own character.")
                          (print "Logging again in 30 seconds...")
                          [error-wait-time login-command])

    *is-old?* (do (print "Game info is old!")
                    [command-wait-time info-command])

    (and (= action-kind :fight) (< hpp hpp-help-critical) enough-energy?)
    (do (print "Critical situaltion...")
        [help-command info-command])

    (and (= action-kind :fight)
         (< action-percents fight-percent-to-help)
         (< hpp hpp-help-default)
         enough-energy?)
    (do (print "Helping hero...")
        [help-command command-wait-time info-command])

    (true? get-card?) (do (print "Whoaaa... A new destiny card!")
                          [get-card-command command-wait-time info-command])

    merge-cards (do (print "Merging cards to get the best! It would be sad to break them.")
                    [(concat merge-cards-command merge-cards)
                    command-wait-time
                    info-command])

    (and (= action-kind :safe) (< action-percents safe-pause-percent))
    (do (print "It's nice to be safe...")
        [safe-wait-time info-command])

    (and (= action-kind :fight) (= current-energy max-energy) enough-energy?)
    (do (print "Gonna get more card points! Helping...")
        [help-command wait-time info-command])

    :else
    (do (print "Boring...")
        [wait-time info-command])))
