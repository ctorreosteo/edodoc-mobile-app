import Charts
import SwiftUI

private enum Passo: Int, CaseIterable {
    case pasti, liberi, energia, allenamento, peso, percezione, impegno
}

/// Una domanda alla volta a schermo intero. L'invio espande la brace su tutto lo schermo.
struct CheckFlowView: View {
    let stato: StatoCheck
    @Environment(AppModel.self) private var app
    @Environment(\.dismiss) private var chiudi
    @Environment(\.accessibilityReduceMotion) private var riduci

    @State private var passo = Passo.pasti
    @State private var avanti = true
    @State private var pasti = 14
    @State private var liberi = ""
    @State private var energia = 0
    @State private var allenamento = ""
    @State private var peso = ""
    @State private var andamento = ""
    @State private var migliorata: Bool?
    @State private var percezione = ""
    @State private var impegno = 0
    @State private var inCorso = false
    @State private var errore: String?
    @State private var inviato = false
    @State private var espansione: CGFloat = 0

    var body: some View {
        ZStack {
            Brace(fuoco: fuoco, intensita: 0.95)
            VStack(spacing: 0) {
                testata
                ScrollView {
                    domanda
                        .id(passo)
                        .transition(transizione)
                        .padding(.horizontal, Tema.margine + 2)
                        .padding(.top, 34)
                        .frame(maxWidth: .infinity, alignment: .leading)
                }
                .scrollBounceBehavior(.basedOnSize)
                .scrollDismissesKeyboard(.interactively)
                piede
            }
            if inviato { conclusione }
        }
        .onAppear(perform: compilaDaEsistente)
    }

    private var fuoco: UnitPoint {
        UnitPoint(x: 0.2 + 0.6 * Double(passo.rawValue) / 6, y: 0.1 + 0.05 * Double(passo.rawValue))
    }

    private var transizione: AnyTransition {
        if riduci { return .opacity }
        return .asymmetric(insertion: .move(edge: avanti ? .trailing : .leading).combined(with: .opacity),
                           removal: .move(edge: avanti ? .leading : .trailing).combined(with: .opacity))
    }

    private var testata: some View {
        HStack(spacing: 14) {
            HStack(spacing: 5) {
                ForEach(Passo.allCases, id: \.self) { p in
                    Capsule()
                        .fill(p.rawValue <= passo.rawValue ? Tema.ember : Tema.hairline)
                        .frame(height: 4)
                }
            }
            .accessibilityElement()
            .accessibilityLabel("Domanda \(passo.rawValue + 1) di \(Passo.allCases.count)")
            Button {
                chiudi()
            } label: {
                Image(systemName: "xmark")
                    .font(.system(size: 14, weight: .semibold))
                    .foregroundStyle(Tema.ink)
                    .frame(width: 44, height: 44)
                    .vetro(raggio: 22, interattivo: true)
            }
            .accessibilityLabel("Chiudi")
        }
        .padding(.horizontal, Tema.margine)
        .padding(.top, 8)
    }

    @ViewBuilder
    private var domanda: some View {
        switch passo {
        case .pasti:
            titolo("Quanti pasti ottimali hai fatto?", "Su 21 della settimana.")
            QuadrantePasti(valore: $pasti)
                .frame(maxWidth: 320)
                .frame(maxWidth: .infinity)
                .padding(.top, 24)
        case .liberi:
            titolo("Com'è andata con i pasti liberi?", "Facoltativo. Racconta pure in poche parole.")
            AreaTesto(segnaposto: "Es. pizza sabato sera", testo: $liberi)
        case .energia:
            titolo("Quanta energia hai avuto?")
            ScalaEllissi(valore: $energia, etichette: ["Pochissima", "Poca", "Nella media", "Buona", "Tanta"])
                .padding(.top, 44)
                .accessibilityIdentifier("check.energia")
        case .allenamento:
            titolo("Com'è andato l'allenamento?", "Facoltativo. Quanto movimento hai fatto e con quali sensazioni.")
            AreaTesto(segnaposto: "Es. 3 camminate da 40 minuti", testo: $allenamento)
        case .peso:
            titolo("Il tuo peso di questa settimana", "Facoltativo. Usa la media, se ti pesi più volte.")
            HStack(alignment: .firstTextBaseline, spacing: 8) {
                TextField("0,0", text: $peso)
                    .keyboardType(.decimalPad)
                    .font(Tipografia.display(72))
                    .monospacedDigit()
                    .foregroundStyle(Tema.ink)
                    .multilineTextAlignment(.center)
                    .fixedSize()
                    .accessibilityLabel("Peso in chilogrammi")
                Text("kg")
                    .font(Tipografia.headline)
                    .foregroundStyle(Tema.inkSecondario)
            }
            .frame(maxWidth: .infinity)
            .padding(.vertical, 26)
            if pesoValido == false {
                Text("Inserisci un peso tra 20 e 400 kg.")
                    .font(Tipografia.nota)
                    .foregroundStyle(Tema.critico)
                    .frame(maxWidth: .infinity)
            }
            AreaTesto(segnaposto: "Note sull'andamento del peso", testo: $andamento)
        case .percezione:
            titolo("Noti dei miglioramenti?", "Nel corpo, nell'umore, nella gestione dei pasti.")
            HStack(spacing: 12) {
                Scelta(testo: "Sì", attiva: migliorata == true) { migliorata = true }
                Scelta(testo: "Non ancora", attiva: migliorata == false) { migliorata = false }
            }
            .padding(.top, 28)
            AreaTesto(segnaposto: "Vuoi aggiungere qualcosa?", testo: $percezione)
        case .impegno:
            titolo("Quanto impegno ci hai messo?")
            ScalaEllissi(valore: $impegno, etichette: ["Pochissimo", "Poco", "Abbastanza", "Molto", "Il massimo"])
                .padding(.top, 44)
                .accessibilityIdentifier("check.impegno")
            if let errore {
                Text(errore)
                    .font(Tipografia.nota)
                    .foregroundStyle(Tema.critico)
                    .padding(.top, 20)
            }
        }
    }

    private func titolo(_ testo: String, _ sotto: String? = nil) -> some View {
        VStack(alignment: .leading, spacing: 10) {
            Text(testo)
                .font(Tipografia.display(38))
                .foregroundStyle(Tema.ink)
                .tracking(-0.4)
                .fixedSize(horizontal: false, vertical: true)
            if let sotto {
                Text(sotto)
                    .font(Tipografia.body)
                    .foregroundStyle(Tema.inkSecondario)
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .accessibilityElement(children: .combine)
        .accessibilityAddTraits(.isHeader)
    }

    private var piede: some View {
        HStack(spacing: 12) {
            if passo != .pasti {
                Button {
                    vai(-1)
                } label: {
                    Image(systemName: "arrow.left").frame(width: 24)
                }
                .buttonStyle(BottoneVetro(larghezzaPiena: false))
                .accessibilityLabel("Indietro")
            }
            Button {
                if passo == .impegno {
                    Task { await invia() }
                } else {
                    vai(1)
                }
            } label: {
                if inCorso {
                    ProgressView().tint(Tema.suEmber)
                } else {
                    Text(passo == .impegno ? "Invia al Dr. Bo" : "Continua")
                }
            }
            .buttonStyle(BottoneEmber())
            .disabled(!puoiProseguire || inCorso)
            .accessibilityIdentifier("check.avanti")
        }
        .padding(.horizontal, Tema.margine)
        .padding(.vertical, 12)
    }

    private var pesoDecimale: Decimal? {
        Decimal(string: peso.replacingOccurrences(of: ",", with: "."), locale: Locale(identifier: "en_US_POSIX"))
    }

    private var pesoValido: Bool? {
        guard !peso.isEmpty else { return nil }
        guard let pesoDecimale else { return false }
        return pesoDecimale >= 20 && pesoDecimale <= 400
    }

    private var puoiProseguire: Bool {
        switch passo {
        case .energia: energia > 0
        case .peso: pesoValido != false
        case .impegno: impegno > 0
        default: true
        }
    }

    private func vai(_ delta: Int) {
        guard let nuovo = Passo(rawValue: passo.rawValue + delta) else { return }
        avanti = delta > 0
        Tocco.leggero()
        withAnimation(riduci ? .easeInOut(duration: 0.2) : .edo) { passo = nuovo }
    }

    private func compilaDaEsistente() {
        guard let c = stato.check else { return }
        pasti = c.pastiOttimali ?? pasti
        liberi = c.pastiLiberi ?? ""
        energia = c.energia ?? 0
        allenamento = c.allenamento ?? ""
        peso = c.pesoKg.map { "\($0)".replacingOccurrences(of: ".", with: ",") } ?? ""
        andamento = c.andamentoPeso ?? ""
        migliorata = c.percezioneMigliorata
        percezione = c.percezione ?? ""
        impegno = c.impegno ?? 0
    }

    private func vuotoANil(_ testo: String) -> String? {
        let pulito = testo.trimmingCharacters(in: .whitespacesAndNewlines)
        return pulito.isEmpty ? nil : pulito
    }

    private func invia() async {
        inCorso = true
        errore = nil
        defer { inCorso = false }
        let risposte = Risposte(pastiOttimali: pasti, pastiLiberi: vuotoANil(liberi), energia: energia,
                                allenamento: vuotoANil(allenamento), pesoKg: pesoDecimale,
                                andamentoPeso: vuotoANil(andamento), percezione: vuotoANil(percezione),
                                percezioneMigliorata: migliorata, impegno: impegno)
        do {
            let _: CheckDettaglio = try await app.api.post("/app/check", risposte)
            Tocco.conferma()
            inviato = true
            withAnimation(riduci ? .easeInOut(duration: 0.3) : .spring(response: 0.9, dampingFraction: 0.9)) { espansione = 1 }
            try? await Task.sleep(for: .seconds(2.8))
            chiudi()
        } catch let errore as ErroreAPI where errore.conflitto {
            // Il dottore ha già risposto: le risposte non si cambiano più, resta la chat
            app.mostra(errore.localizedDescription)
            chiudi()
        } catch {
            self.errore = error.localizedDescription
            Tocco.errore()
        }
    }

    private var conclusione: some View {
        GeometryReader { spazio in
            let diametro = hypot(spazio.size.width, spazio.size.height) * 2.2
            ZStack {
                Ellipse()
                    .fill(Tema.ember)
                    .frame(width: diametro, height: diametro)
                    .scaleEffect(riduci ? 1 : max(espansione, 0.001))
                    .opacity(riduci ? espansione : 1)
                    .position(x: spazio.size.width / 2, y: spazio.size.height)
                VStack(alignment: .leading, spacing: 12) {
                    Text("Grazie, \(app.utente?.nome ?? "").")
                        .font(Tipografia.display(50))
                    Text("Il tuo check è arrivato al Dr. Bo.")
                        .font(Tipografia.body)
                }
                .foregroundStyle(Tema.suEmber)
                .frame(maxWidth: .infinity, alignment: .leading)
                .padding(.horizontal, Tema.margine + 4)
                .opacity(espansione)
                .accessibilityIdentifier("check.grazie")
            }
        }
        .ignoresSafeArea()
    }
}

private struct AreaTesto: View {
    let segnaposto: String
    @Binding var testo: String

    var body: some View {
        TextField(segnaposto, text: $testo, axis: .vertical)
            .font(Tipografia.body)
            .foregroundStyle(Tema.ink)
            .lineLimit(3...7)
            .padding(18)
            .vetro(raggio: Tema.raggioControllo + 4, interattivo: true)
            .padding(.top, 28)
    }
}

private struct Scelta: View {
    let testo: String
    let attiva: Bool
    let tocca: () -> Void

    var body: some View {
        Button {
            Tocco.leggero()
            withAnimation(.edo) { tocca() }
        } label: {
            Text(testo)
                .font(Tipografia.bodyMedio)
                .foregroundStyle(attiva ? Tema.suEmber : Tema.ink)
                .frame(maxWidth: .infinity, minHeight: 64)
                .background(attiva ? AnyShapeStyle(Tema.ember) : AnyShapeStyle(Tema.hairline),
                            in: RoundedRectangle(cornerRadius: Tema.raggioControllo, style: .continuous))
        }
        .buttonStyle(Premibile())
        .accessibilityAddTraits(attiva ? .isSelected : [])
    }
}

/// Quadrante da trascinare: 21 tacche, una per ogni pasto della settimana.
struct QuadrantePasti: View {
    @Binding var valore: Int
    private let massimo = 21

    var body: some View {
        GeometryReader { spazio in
            let lato = min(spazio.size.width, spazio.size.height)
            let frazione = CGFloat(valore) / CGFloat(massimo)
            ZStack {
                Canvas { contesto, dimensioni in
                    let centro = CGPoint(x: dimensioni.width / 2, y: dimensioni.height / 2)
                    let raggio = dimensioni.width / 2 - 20
                    for tacca in 0..<massimo {
                        let angolo = (Double(tacca) + 0.5) / Double(massimo) * 2 * .pi - .pi / 2
                        let accesa = tacca < valore
                        let lunghezza: CGFloat = accesa ? 22 : 12
                        var linea = Path()
                        linea.move(to: CGPoint(x: centro.x + cos(angolo) * (raggio - lunghezza), y: centro.y + sin(angolo) * (raggio - lunghezza)))
                        linea.addLine(to: CGPoint(x: centro.x + cos(angolo) * raggio, y: centro.y + sin(angolo) * raggio))
                        contesto.stroke(linea, with: .color(accesa ? Tema.ember : Tema.inkTerziario),
                                        style: StrokeStyle(lineWidth: accesa ? 5 : 2.5, lineCap: .round))
                    }
                }
                let angolo = Double(frazione) * 2 * .pi - .pi / 2
                Ellipse()
                    .fill(Tema.ink)
                    .frame(width: 26, height: 20)
                    .rotationEffect(.radians(angolo + .pi / 2))
                    .offset(x: cos(angolo) * (lato / 2 - 20 + 12), y: sin(angolo) * (lato / 2 - 20 + 12))
                VStack(spacing: 0) {
                    Text("\(valore)")
                        .font(Tipografia.display(88))
                        .foregroundStyle(Tema.ink)
                        .contentTransition(.numericText(value: Double(valore)))
                    Text("pasti")
                        .font(Tipografia.label)
                        .foregroundStyle(Tema.inkSecondario)
                }
            }
            .frame(width: lato, height: lato)
            .contentShape(Circle())
            .gesture(DragGesture(minimumDistance: 0).onChanged { tocco in
                aggiorna(tocco.location, lato: lato)
            })
        }
        .aspectRatio(1, contentMode: .fit)
        .accessibilityElement()
        .accessibilityLabel("Pasti ottimali")
        .accessibilityValue("\(valore) su \(massimo)")
        .accessibilityAdjustableAction { direzione in
            valore = direzione == .increment ? min(massimo, valore + 1) : max(0, valore - 1)
        }
        .accessibilityIdentifier("check.quadrante")
    }

    private func aggiorna(_ punto: CGPoint, lato: CGFloat) {
        var angolo = atan2(punto.y - lato / 2, punto.x - lato / 2) + .pi / 2
        if angolo < 0 { angolo += 2 * .pi }
        let nuovo = Int((angolo / (2 * .pi) * CGFloat(massimo)).rounded())
        // Il dito che passa da 21 a 0 attraverso le ore 12 non deve azzerare tutto
        guard abs(nuovo - valore) < massimo / 2, nuovo != valore else { return }
        Tocco.leggero()
        withAnimation(.interactiveSpring) { valore = nuovo }
    }
}

// MARK: Storico

struct StoricoCheckView: View {
    @Environment(AppModel.self) private var app

    var body: some View {
        Caricamento {
            let storico: [CheckDettaglio] = try await app.api.get("/app/check")
            return storico
        } contenuto: { storico, aggiorna in
            if storico.isEmpty {
                StatoVuoto(titolo: "Nessun check", testo: "Qui troverai ogni settimana e le risposte del Dr. Bo.", icona: "calendar")
            } else {
                List {
                    let pesi = storico.compactMap { c in c.pesoKg.map { (c.settimana, $0) } }.sorted { $0.0 < $1.0 }
                    if pesi.count > 1 {
                        GraficoPeso(punti: pesi).rigaEdo().listRowSeparator(.hidden)
                    }
                    ForEach(storico) { check in
                        NavigationLink { FeedbackView(check: check) } label: {
                            VStack(alignment: .leading, spacing: 4) {
                                Text("Settimana del \(check.settimana.breve)")
                                    .font(Tipografia.bodyMedio)
                                    .foregroundStyle(Tema.ink)
                                Text(riassunto(check))
                                    .font(Tipografia.nota)
                                    .foregroundStyle(Tema.inkSecondario)
                            }
                        }
                        .rigaEdo()
                    }
                }
                .listaEdo()
                .refreshable { await aggiorna() }
            }
        }
        .telaEdo()
        .navigationTitle("I tuoi check")
    }

    private func riassunto(_ check: CheckDettaglio) -> String {
        var parti: [String] = []
        if let pasti = check.pastiOttimali { parti.append("\(pasti)/21 pasti") }
        if let peso = check.pesoKg { parti.append(Formati.peso(peso)) }
        if check.feedbackAudioId != nil {
            parti.append("Vocale")
        } else if check.haFeedback {
            parti.append("Risposta")
        }
        return parti.joined(separator: " · ")
    }
}

private struct GraficoPeso: View {
    let punti: [(Giorno, Decimal)]

    var body: some View {
        let valori = punti.map { NSDecimalNumber(decimal: $0.1).doubleValue }
        let minimo = (valori.min() ?? 0) - 1
        let massimo = (valori.max() ?? 0) + 1
        VStack(alignment: .leading, spacing: 12) {
            NumeroEroe(valore: Formati.peso(punti.last?.1), didascalia: "Ultimo peso", dimensione: 44)
            Chart(Array(zip(punti.indices, valori)), id: \.0) { indice, valore in
                LineMark(x: .value("Settimana", punti[indice].0.data), y: .value("Peso", valore))
                    .interpolationMethod(.catmullRom)
                    .foregroundStyle(Tema.ember)
                    .lineStyle(StrokeStyle(lineWidth: 2.5, lineCap: .round))
            }
            .chartYScale(domain: minimo...massimo)
            .chartXAxis(.hidden)
            .chartYAxis(.hidden)
            .frame(height: 90)
        }
        .padding(.vertical, 8)
        .accessibilityElement(children: .combine)
    }
}
