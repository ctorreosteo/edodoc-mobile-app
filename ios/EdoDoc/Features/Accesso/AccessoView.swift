import SwiftUI

struct AccessoView: View {
    @Environment(AppModel.self) private var modello
    @State private var email = ""
    @State private var password = ""
    @State private var inCorso = false
    @State private var errore: String?
    @State private var primoAccesso = false
    @FocusState private var campo: Campo?

    private enum Campo { case email, password }

    var body: some View {
        ZStack {
            Brace(fuoco: .init(x: 0.5, y: 0.3), intensita: 1.1)
            ScrollView {
                VStack(alignment: .leading, spacing: 0) {
                    Image("Logo")
                        .resizable()
                        .renderingMode(.template)
                        .scaledToFit()
                        .frame(height: 30)
                        .foregroundStyle(Tema.ember)
                        .accessibilityLabel("Dr. Edoardo Bo")
                    Spacer(minLength: 110)
                    Text("Eccoti.")
                        .font(Tipografia.display(52))
                        .foregroundStyle(Tema.ink)
                        .minimumScaleFactor(0.7)
                        .lineLimit(1)
                        .padding(.bottom, 8)
                    Text("Il tuo percorso con il Dr. Bo ti aspetta.")
                        .font(Tipografia.body)
                        .foregroundStyle(Tema.inkSecondario)
                        .padding(.bottom, 36)
                    VStack(spacing: 0) {
                        TextField("Email", text: $email)
                            .textContentType(.username)
                            .keyboardType(.emailAddress)
                            .textInputAutocapitalization(.never)
                            .autocorrectionDisabled()
                            .focused($campo, equals: .email)
                            .submitLabel(.next)
                            .onSubmit { campo = .password }
                            .padding(18)
                            .accessibilityIdentifier("accesso.email")
                        Filetto()
                        SecureField("Password", text: $password)
                            .textContentType(.password)
                            .focused($campo, equals: .password)
                            .submitLabel(.go)
                            .onSubmit { Task { await accedi() } }
                            .padding(18)
                            .accessibilityIdentifier("accesso.password")
                    }
                    .font(Tipografia.body)
                    .foregroundStyle(Tema.ink)
                    .vetro(raggio: Tema.raggioControllo + 4)
                    .padding(.bottom, 14)
                    if let errore {
                        Text(errore)
                            .font(Tipografia.nota)
                            .foregroundStyle(Tema.critico)
                            .padding(.horizontal, 6)
                            .padding(.bottom, 14)
                            .accessibilityIdentifier("accesso.errore")
                    }
                    Button {
                        Task { await accedi() }
                    } label: {
                        Group {
                            if inCorso { ProgressView().tint(Tema.suEmber) } else { Text("Accedi") }
                        }
                    }
                    .buttonStyle(BottoneEmber())
                    .disabled(inCorso || email.isEmpty || password.isEmpty)
                    .accessibilityIdentifier("accesso.invia")
                    Button("È il tuo primo accesso?") { primoAccesso = true }
                        .font(Tipografia.label)
                        .foregroundStyle(Tema.inkSecondario)
                        .frame(maxWidth: .infinity, minHeight: 44)
                        .padding(.top, 8)
                        .accessibilityIdentifier("accesso.primo")
                    Link("Privacy", destination: Legale.privacy)
                        .font(Tipografia.nota)
                        .foregroundStyle(Tema.inkSecondario)
                        .frame(maxWidth: .infinity, minHeight: 44)
                }
                .padding(.horizontal, Tema.margine + 4)
                .padding(.vertical, 24)
                .frame(maxWidth: 520)
                .frame(maxWidth: .infinity)
            }
            .scrollBounceBehavior(.basedOnSize)
            .scrollDismissesKeyboard(.interactively)
        }
        .sheet(isPresented: $primoAccesso) {
            PrimoAccessoView(token: modello.invitoInSospeso)
        }
        .onChange(of: modello.invitoInSospeso) { _, token in
            if token != nil { primoAccesso = true }
        }
        .onAppear {
            if modello.invitoInSospeso != nil { primoAccesso = true }
        }
    }

    private func accedi() async {
        guard !inCorso, !email.isEmpty, !password.isEmpty else { return }
        inCorso = true
        errore = nil
        defer { inCorso = false }
        do {
            try await modello.accedi(email: email, password: password)
            Tocco.conferma()
        } catch {
            errore = error.localizedDescription
            Tocco.errore()
        }
    }
}

struct PrimoAccessoView: View {
    @Environment(AppModel.self) private var modello
    @Environment(\.dismiss) private var chiudi
    let token: String?
    @State private var link = ""
    @State private var password = ""
    @State private var conferma = ""
    @State private var inCorso = false
    @State private var errore: String?

    /// Nei test UI il suggerimento di password forte di iOS sovrascrive i campi.
    private var tipoNuovaPassword: UITextContentType? {
        #if DEBUG
        if ProcessInfo.processInfo.arguments.contains("-edodoc.senzaPush") { return .oneTimeCode }
        #endif
        return .newPassword
    }

    private var tokenScelto: String? { token ?? InvitoLink.token(da: link) }
    private var valido: Bool { tokenScelto != nil && password.count >= 10 && password == conferma }

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 20) {
                    Text("Crea la tua password.")
                        .font(Tipografia.display(38))
                        .foregroundStyle(Tema.ink)
                    Text(token == nil
                         ? "Incolla il link dell'email di invito del Dr. Bo, poi scegli una password di almeno 10 caratteri."
                         : "Invito riconosciuto. Scegli una password di almeno 10 caratteri.")
                        .font(Tipografia.body)
                        .foregroundStyle(Tema.inkSecondario)
                    VStack(spacing: 0) {
                        if token == nil {
                            TextField("Link di invito", text: $link, axis: .vertical)
                                .textInputAutocapitalization(.never)
                                .autocorrectionDisabled()
                                .padding(18)
                                .accessibilityIdentifier("primo.link")
                            Filetto()
                        }
                        SecureField("Nuova password", text: $password)
                            .textContentType(tipoNuovaPassword)
                            .padding(18)
                            .accessibilityIdentifier("primo.password")
                        Filetto()
                        SecureField("Ripeti la password", text: $conferma)
                            .textContentType(tipoNuovaPassword)
                            .padding(18)
                            .accessibilityIdentifier("primo.conferma")
                    }
                    .font(Tipografia.body)
                    .vetro(raggio: Tema.raggioControllo + 4)
                    if !conferma.isEmpty && password != conferma {
                        Text("Le due password non coincidono.")
                            .font(Tipografia.nota)
                            .foregroundStyle(Tema.critico)
                    }
                    if let errore {
                        Text(errore)
                            .font(Tipografia.nota)
                            .foregroundStyle(Tema.critico)
                    }
                    Button("Entra nell'app") { Task { await invia() } }
                        .buttonStyle(BottoneEmber())
                        .disabled(!valido || inCorso)
                }
                .padding(Tema.margine)
            }
            .telaEdo()
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Chiudi") { chiudi() }
                }
            }
        }
    }

    private func invia() async {
        guard let tokenScelto else { return }
        inCorso = true
        errore = nil
        defer { inCorso = false }
        do {
            try await modello.impostaPassword(token: tokenScelto, password: password)
            chiudi()
        } catch {
            errore = error.localizedDescription
        }
    }
}
