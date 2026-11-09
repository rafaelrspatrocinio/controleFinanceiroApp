# Meu Caixa — Java

Aplicativo de controle financeiro em Java, disponível como desktop Swing e como aplicação web local. As versões compartilham o modelo de lançamentos e os cálculos financeiros, mas têm interfaces e formas de persistência distintas.

## Recursos

- Resumo mensal de receitas, despesas e saldo acumulado.
- Navegação entre meses.
- Cadastro e exclusão de lançamentos, individualmente ou todos de uma vez.
- Ajuste do saldo inicial; limpar lançamentos não altera esse saldo.
- Importação e exportação de cópias CSV.
- Inicialização sem lançamentos quando ainda não existem dados locais.

## Organização do código

As classes Java são separadas por responsabilidade dentro de `br.com.meucaixa`: `domain` contém o modelo e os cálculos, `persistence` contém as implementações de armazenamento, `web` contém os controladores HTTP, `desktop` reúne a interface Swing e `bootstrap` concentra a carga inicial. A classe principal Spring Boot permanece no pacote raiz para descobrir os componentes dos subpacotes.

## Aplicativo web local

A versão web usa Spring Boot 2.7, Thymeleaf e banco H2 persistente em arquivo. A interface e o backend Java são servidos pela mesma aplicação; ao executar sem Docker, o banco fica em `%USERPROFILE%\.meu-caixa-web\`.

### Executar com Docker

Com Docker Desktop instalado e iniciado, na pasta do projeto execute:

```bat
docker compose up --build -d
```

Abra `http://localhost:8080`. Para acompanhar os logs use `docker compose logs -f`; para parar sem apagar os dados use `docker compose down`. O Compose mantém o banco H2 no volume persistente `meu-caixa-data`, mesmo ao recriar o container. Faça cópias adicionais usando **Exportar cópia** na aplicação.

Por padrão, a porta fica acessível apenas no próprio computador. Não altere o endereço de bind para expor na rede sem configurar autenticação e HTTPS. Não use `docker compose down -v` a menos que queira apagar permanentemente o volume e os dados.

O Dockerfile compila o JAR web dentro de uma imagem Maven/Java e o container executa com usuário não-root. O Docker não é necessário para usar os scripts Windows abaixo.

### Executar sem Docker

1. Instale Java 8 ou mais recente.
2. Compile uma vez com `compilar-web.bat` (requer Maven e internet na primeira compilação).
3. Inicie com `executar-web.bat`.
4. Abra `http://localhost:8080` no navegador.

Também é possível executar na pasta do projeto:

```bat
mvn clean package
java -jar target/controle-de-caixa-1.0.0-web.jar
```

O servidor web fica restrito ao próprio computador (`127.0.0.1`), sem autenticação; não exponha a porta a uma rede ou à internet. Esta primeira versão é destinada ao uso pessoal local. Para acesso remoto/multiusuário, será necessário configurar autenticação, HTTPS, rede e um banco servidor apropriado.

Na primeira inicialização web, o sistema importa o CSV e o saldo da versão desktop em `%USERPROFILE%\.meu-caixa-java\` se existirem; se não, carrega os dados de exemplo. A migração é feita apenas uma vez e não modifica nem apaga os arquivos originais. Após importar, as versões desktop e web têm cópias de dados independentes; faça exportação/importação CSV para transferir alterações entre elas.

## Aplicativo desktop no Windows

1. Instale Java 8 ou mais recente.
2. Dê dois cliques em `executar.bat`.

O JAR compilado está em `target/controle-de-caixa-1.0.0.jar`. Também pode iniciá-lo pelo terminal, nesta pasta, com:

```bat
java -jar target/controle-de-caixa-1.0.0.jar
```

Para executar em outro computador, copie a pasta do projeto ou o JAR. Basta ter Java instalado; não é necessário instalar Node.js, npm ou Maven para executar os aplicativos já compilados.

## Compilar o projeto

Instale o JDK e o Maven e execute `compilar.bat` para compilar o desktop, ou `compilar-web.bat` para a versão web. O comando `mvn clean package` compila e empacota ambas as versões. O Maven pode precisar de internet para baixar dependências.

## Dados e cópias de segurança

A versão desktop mantém lançamentos em `%USERPROFILE%\.meu-caixa-java\transactions.csv` e saldo inicial em `settings.properties`, na mesma pasta. A versão web mantém seus dados no banco H2 em `%USERPROFILE%\.meu-caixa-web\`. Os bancos são locais e não são sincronizados automaticamente.

Use **Exportar cópia** para salvar lançamentos em CSV e **Importar cópia** para restaurá-los ou transferi-los. A importação substitui os movimentos atuais após confirmação. O saldo inicial deve ser ajustado separadamente em **Ajustes**.

Os lançamentos e saldos financeiros ficam nos dados locais e não devem ser adicionados ao repositório. Uma instalação sem dados inicia sem lançamentos.

## Testes

```bat
mvn clean package
java -cp "target/classes;target/test-classes" br.com.meucaixa.domain.service.FinanceServiceTest
```

Os testes existentes verificam cálculos mensais, saldo acumulado, leitura de valores em reais, persistência local e cópias CSV com acentos, vírgulas e aspas.
