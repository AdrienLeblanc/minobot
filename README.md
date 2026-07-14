# Minobot

Minobot est un petit logiciel de confort pour **Dofus Retro**, destiné aux joueurs qui font tourner
plusieurs personnages en même temps.

Il ne triche pas et ne joue pas à votre place. Il reste discrètement en fond, à côté de l'horloge de
Windows, et attend que vous appuyiez sur une touche pour vous épargner les manipulations pénibles du
multi-compte : cliquer neuf fois la même chose dans neuf fenêtres, inviter tout le monde en groupe un
par un, chercher la bonne fenêtre dans la barre des tâches.

---

## Ce qu'il sait faire

### Cliquer dans toutes les fenêtres à la fois — touche `X1`

C'est la fonction principale. Vous placez votre souris quelque part dans le jeu, vous appuyez sur le
bouton latéral de votre souris (`X1`, celui sous le pouce), et **tous vos personnages cliquent au même
endroit que vous**. Pratique pour ramasser se déplacer, entrer dans un zaap ou valider une fenêtre
sur toute la team d'un coup.

Deux choses à savoir :

- **Votre personnage principal ne perd jamais le focus.** Vous continuez à jouer normalement pendant
  que les autres cliquent en arrière-plan.
- Windows a la mauvaise habitude de faire **clignoter en orange** les fenêtres en arrière-plan qui
  reçoivent un clic. Minobot fait de son mieux pour l'éviter, mais si des fenêtres clignotent quand
  même, appuyez sur **`Shift+X1`** : il passe sur chacune d'elles pour éteindre le clignotement.

### Inviter tout le monde en groupe — touche `F8`

Une seule touche et toute votre team se retrouve en groupe. Le personnage au premier plan devient le
chef : il invite le deuxième, qui accepte et invite le troisième, et ainsi de suite jusqu'au dernier.

Minobot attend la **notification du jeu** à chaque étape pour savoir que l'invitation est bien
arrivée, plutôt que d'enchaîner à l'aveugle. C'est ce qui le rend fiable même quand le jeu rame.

### Passer d'un personnage à l'autre — touches `X2` et `Shift+X2`

`X2` (l'autre bouton latéral de la souris) fait défiler vos personnages **dans l'ordre que vous avez
choisi**, et `Shift+X2` dans l'autre sens. Bien plus confortable qu'`Alt+Tab`, qui vous envoie
n'importe où.

Seules les fenêtres **de l'écran sur lequel vous êtes** participent au défilement. Si vous jouez sur
deux écrans, chacun garde son propre cycle.

### Ranger la barre des tâches — touche `F9`

Vos fenêtres de jeu se lancent dans le désordre dans la barre des tâches ? `F9` les remet dans l'ordre
que vous avez configuré.

> ⚠️ Pendant l'opération, **vos fenêtres disparaissent quelques instants** : c'est normal, c'est le
> seul moyen de forcer Windows à les réafficher dans le bon ordre. Elles reviennent toutes seules.

### Ramener le bon personnage à l'écran — automatique

Quand un de vos personnages en arrière-plan se fait attaquer, recevoir un message ou inviter, le jeu
affiche une notification Windows. Minobot la voit et **bascule automatiquement sur ce personnage**.

---

## Installation

1. Téléchargez Minobot et **décompressez le dossier où vous voulez** (sur le bureau, dans vos
   documents, peu importe).
2. Lancez **`Minobot.exe`**.
3. C'est tout. Il n'y a rien à installer, pas même Java : tout voyage dans le dossier.

Une **icône apparaît à côté de l'horloge**, en bas à droite de l'écran : c'est le signe que Minobot
tourne. Pour l'arrêter, faites un clic droit dessus et choisissez **Quitter**.

> Si vous ne voyez pas l'icône, cliquez sur la petite flèche `^` à côté de l'horloge : Windows a
> tendance à cacher les icônes récentes.

---

## Configuration

Au premier lancement, Minobot crée un fichier **`config.json`** juste à côté de `Minobot.exe`. C'est
le seul fichier que vous aurez à toucher. Ouvrez-le avec le Bloc-notes.

![img.png](assets/img.png)

### La seule chose vraiment obligatoire : la liste de vos personnages

Remplacez la liste `window_cycle_order` par **les noms de vos personnages, dans l'ordre où vous voulez
qu'ils défilent** :

![img_1.png](assets/img_1.png)

Cet ordre sert à deux choses : le défilement des fenêtres (`X2`) et le rangement de la barre des
tâches (`F9`).

**Après chaque modification du fichier, quittez Minobot et relancez-le** pour qu'il en tienne compte.

### Les autres réglages

Tout le reste est facultatif. Voici le fichier au complet — il n'y a rien de caché ailleurs :

```json
{
  "log_level": "INFO",

  "multiclick_hotkey": "x1",
  "multiclick_exclude": [],
  "reset_windows_hotkey": "shift+x1",

  "group_invite_hotkey": "F8",

  "window_cycle_order": [
    "PremierPerso",
    "DeuxiemePerso",
    "TroisiemePerso"
  ],
  "window_cycle_next_hotkey": "x2",
  "window_cycle_prev_hotkey": "shift+x2",

  "window_reorder_hotkey": "F9"
}
```

| Réglage | À quoi ça sert |
| --- | --- |
| `window_cycle_order` | Vos personnages, dans l'ordre. **Le réglage principal.** |
| `multiclick_exclude` | Les personnages à **laisser en dehors** du clic multiple. Exemple : `["Mule", "Marchand"]` — votre mule en mode marchand ne bougera pas. |
| `..._hotkey` | Les touches de chaque fonction. Voir la liste ci-dessous. |
| `log_level` | Mettez `"DEBUG"` à la place de `"INFO"` si vous devez signaler un problème : Minobot écrira beaucoup plus de détails dans son journal. |

Une ligne que vous supprimez du fichier reprend simplement sa valeur d'origine. Un fichier qui ne
contiendrait que votre `window_cycle_order` fonctionne très bien.

**Pour désactiver une fonction, laissez sa touche vide** : `"group_invite_hotkey": ""` et les
invitations de groupe ne répondront plus, sans rien casser d'autre.

---

## Les touches disponibles

Une touche se compose d'une **touche principale**, éventuellement précédée d'un ou plusieurs
**modificateurs**, reliés par un `+`. Les majuscules n'ont aucune importance : `shift+x1` et `SHIFT+X1`
sont identiques.

**Les touches principales** — vous devez en choisir une dans cette liste, il n'y en a pas d'autres :

| À écrire | La touche correspondante |
| --- | --- |
| `F1` à `F12` | Les touches de fonction, en haut du clavier. |
| `x1` | Le bouton latéral « Précédent » de la souris (sous le pouce). |
| `x2` | Le bouton latéral « Suivant » de la souris. |
| `left` | Le **clic gauche** de la souris. |
| `right` | Le **clic droit** de la souris. |
| `middle` | Le **clic molette** (appuyer sur la roulette). |

**Les modificateurs** — aucun, un, ou plusieurs : `ctrl`, `shift`, `alt`.

Quelques exemples valables :

```json
"group_invite_hotkey": "F8",
"multiclick_hotkey": "x1",
"reset_windows_hotkey": "shift+x1",
"window_reorder_hotkey": "ctrl+alt+F9"
```

Trois choses à savoir avant de choisir :

- **Les lettres et les chiffres ne sont pas utilisables.** `a`, `1` ou `ctrl+s` ne fonctionneront pas.
  C'est volontaire : ce sont les touches avec lesquelles vous écrivez dans le chat du jeu, et un
  raccourci posé dessus se déclencherait au milieu de vos phrases.
- **`left`, `right` et `middle` sont les boutons de la souris, pas les flèches du clavier.** Mettre
  une fonction sur `left`, c'est la déclencher à **chacun de vos clics gauches**, partout. C'est
  utilisable pour le clic multiple si vous voulez que votre clic lui-même serve de déclencheur, mais
  c'est le seul cas où ça a du sens.
- **La combinaison la plus précise gagne.** Si `x2` et `shift+x2` sont tous les deux utilisés, appuyer
  sur `X2` en maintenant Shift ne déclenche que le second — jamais les deux à la fois.

---

## En cas de problème

Minobot tient un journal de tout ce qu'il fait dans le fichier **`logs/minobot.log`**, à côté de
`Minobot.exe`. C'est le premier endroit à regarder, et c'est le fichier à joindre si vous signalez un
souci.

| Symptôme | Ce qu'il faut vérifier |
| --- | --- |
| Rien ne se passe quand j'appuie sur une touche | Minobot tourne-t-il ? Cherchez l'icône à côté de l'horloge. Et avez-vous bien **relancé** le logiciel après avoir modifié `config.json` ? |
| Une seule fonction ne répond plus | Vous avez sans doute écrit une touche que Minobot ne connaît pas. Ouvrez `logs/minobot.log` et cherchez une ligne `Hotkey main key ... is not supported.` : les autres fonctions continuent de tourner, seule celle-là est désactivée. |
| Les fenêtres clignotent en orange | Appuyez sur `Shift+X1`. |
| Le défilement `X2` saute des personnages | Vérifiez l'orthographe des noms dans `window_cycle_order`, et souvenez-vous que seules les fenêtres **de l'écran courant** défilent. |
| Le fichier `config.json` semble ignoré | Il doit être **à côté de `Minobot.exe`**, et rester un fichier valide : une virgule oubliée suffit à le casser. Dans ce cas Minobot repart sur ses réglages d'origine et le note dans son journal. |
